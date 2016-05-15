/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.zouzias.spark.lucenerdd

import org.apache.lucene.document.Document
import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.zouzias.spark.lucenerdd.aggregate.{SparkFacetResultMonoid, SparkScoreDocAggregatable}
import org.zouzias.spark.lucenerdd.impl.InMemoryLuceneRDDPartition
import org.zouzias.spark.lucenerdd.models.{SparkFacetResult, SparkScoreDoc}

import scala.reflect.ClassTag

/**
 * Spark RDD with Lucene's query capabilities (term, prefix, fuzzy, phrase query)
 * @tparam T
 */
class LuceneRDD[T: ClassTag](private val partitionsRDD: RDD[AbstractLuceneRDDPartition[T]])
  extends RDD[T](partitionsRDD.context, List(new OneToOneDependency(partitionsRDD)))
  with SparkScoreDocAggregatable {

override protected def getPartitions: Array[Partition] = partitionsRDD.partitions

  override protected def getPreferredLocations(s: Partition): Seq[String] =
    partitionsRDD.preferredLocations(s)

  override def persist(newLevel: StorageLevel): this.type = {
    partitionsRDD.persist(newLevel)
    this
  }

  override def unpersist(blocking: Boolean = true): this.type = {
    partitionsRDD.unpersist(blocking)
    this
  }

  override def setName(_name: String): this.type = {
    partitionsRDD.setName(_name)
    this
  }

  /**
   * Aggregates lucene documents using monoidal structure, i.e., [[SparkDocTopKMonoid]]
   * @param f
   * @return
   */
  private def docResultsAggregator(f: AbstractLuceneRDDPartition[T] => Iterable[SparkScoreDoc])
  : Iterable[SparkScoreDoc] = {
    val parts = partitionsRDD.map(f(_)).map(SparkDocTopKMonoid.build(_))
    parts.reduce(SparkDocTopKMonoid.plus(_, _)).items
  }

  /**
   * Aggregator of faceted results
   * @param f
   * @return
   */
  private def facetResultsAggregator(f: AbstractLuceneRDDPartition[T] => SparkFacetResult)
  : SparkFacetResult = {
    partitionsRDD.map(f(_)).reduce( (x, y) => SparkFacetResultMonoid.plus(x, y))
  }


  /**
   * Return all document fields
   * @return
   */
  def fields(): Set[String] = {
    partitionsRDD.map(_.fields()).reduce(_ ++ _)
  }

  /**
   * Lucene generic query
   * @param doc
   * @return
   */
  def exists(doc: Map[String, String]): Boolean = {
    docResultsAggregator(_.multiTermQuery(doc, DefaultTopK)).nonEmpty
  }

  /**
   * Generic query using Lucene's query parser
   * @param searchString  Query String
   * @param topK
   * @return
   */
  def query(searchString: String,
            topK: Int = DefaultTopK): Iterable[SparkScoreDoc] = {
    docResultsAggregator(_.query(searchString, topK))
  }

  def facetQuery(searchString: String,
                 facetField: String,
                 topK: Int = DefaultTopK): SparkFacetResult = {
    facetResultsAggregator(_.facetQuery(searchString, facetField, topK))
  }

  /**
   * Lucene term query
   * @param fieldName Name of field
   * @param query Term to search on
   * @param topK Number of documents to return
   * @return
   */
  def termQuery(fieldName: String, query: String,
                topK: Int = DefaultTopK): Iterable[SparkScoreDoc] = {
    docResultsAggregator(_.termQuery(fieldName, query, topK))
  }

  /**
   * Lucene prefix query
   * @param fieldName Name of field
   * @param query Prefix query text
   * @param topK Number of documents to return
   * @return
   */
  def prefixQuery(fieldName: String, query: String,
                  topK: Int = DefaultTopK): Iterable[SparkScoreDoc] = {
    docResultsAggregator(_.prefixQuery(fieldName, query, topK))
  }

  /**
   * Lucene fuzzy query
   * @param fieldName Name of field
   * @param query Query text
   * @param maxEdits Fuzziness, edit distance
   * @param topK Number of documents to return
   * @return
   */
  def fuzzyQuery(fieldName: String, query: String,
                 maxEdits: Int, topK: Int = DefaultTopK): Iterable[SparkScoreDoc] = {
    docResultsAggregator(_.fuzzyQuery(fieldName, query, maxEdits, topK))
  }

  /**
   * Lucene phrase Query
   * @param fieldName Name of field
   * @param query Query text
   * @param topK Number of documents to return
   * @return
   */
  def phraseQuery(fieldName: String, query: String,
                  topK: Int = DefaultTopK): Iterable[SparkScoreDoc] = {
    docResultsAggregator(_.phraseQuery(fieldName, query, topK))
  }

  override def count(): Long = {
    partitionsRDD.map(_.size).reduce(_ + _)
  }

  /** RDD compute method. */
  override def compute(part: Partition, context: TaskContext): Iterator[T] = {
    firstParent[AbstractLuceneRDDPartition[T]].iterator(part, context).next.iterator
  }

  override def filter(pred: T => Boolean): LuceneRDD[T] = {
    val newPartitionRDD = partitionsRDD.mapPartitions(partition =>
      partition.map(_.filter(pred)), preservesPartitioning = true
    )
    new LuceneRDD(newPartitionRDD)
  }

  def exists(elem: T): Boolean = {
    partitionsRDD.map(_.isDefined(elem)).collect().exists(x => x)
  }

  def close(): Unit = {
    partitionsRDD.foreach(_.close())
  }
}

object LuceneRDD {

  /**
   * Instantiate a LuceneRDD given an RDD[T]
   * @param elems RDD of type T
   * @param docConversion Implicit conversion of T to Document
   * @tparam T Generic type
   * @return
   */
  def apply[T: ClassTag](elems: RDD[T])(implicit docConversion: T => Document): LuceneRDD[T] = {
    val partitions = elems.mapPartitions[AbstractLuceneRDDPartition[T]](
      iter => Iterator(InMemoryLuceneRDDPartition(iter)),
      preservesPartitioning = true)
    new LuceneRDD(partitions)
  }

  /**
   * Instantiate a LuceneRDD with an iterable
   * @param elems
   * @param docConversion
   * @param sc
   * @tparam T
   * @return
   */
  def apply[T: ClassTag]
  (elems: Iterable[T])(implicit docConversion: T => Document, sc: SparkContext): LuceneRDD[T] = {
    apply(sc.parallelize[T](elems.toSeq))
  }
}