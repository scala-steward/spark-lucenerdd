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
package org.zouzias.spark.lucenerdd.config

import org.apache.lucene.index.IndexOptions

/**
 * Configuration for [[org.zouzias.spark.lucenerdd.LuceneRDD]]
 */
trait LuceneRDDConfigurable extends Configurable {

  protected val MaxDefaultTopKValue: Int = {
    if (Config.hasPath("lucenerdd.query.topk.default")) {
      Config.getInt("lucenerdd.query.topk.maxvalue")
    }
    else 1000
  }

  /** Default value for topK queries */
  protected val DefaultTopK: Int = {
    if (Config.hasPath("lucenerdd.query.topk.default")) {
      Config.getInt("lucenerdd.query.topk.default")
    }
    else 10
  }

  protected val DefaultFacetNum: Int = {
    if (Config.hasPath("lucenerdd.query.facet.topk.default")) {
      Config.getInt("lucenerdd.query.facet.topk.default")
    }
    else 10
  }

  protected val TextFieldsAnalyzed: Boolean = {
    if (Config.hasPath("lucenerdd.index.textfields.analyzed")) {
      Config.getBoolean("lucenerdd.index.textfields.analyzed")
    }
    else true
  }

  protected val TextFieldsStoreTermVector: Boolean = {
    if (Config.hasPath("lucenerdd.index.textfields.termvectors")) {
      Config.getBoolean("lucenerdd.index.textfields.termvectors")
    }
    else true
  }

  protected val TextFieldsStoreTermPositions: Boolean = {
    if (Config.hasPath("lucenerdd.index.textfields.termpositions")) {
      Config.getBoolean("lucenerdd.index.textfields.termpositions")
    }
    else true
  }

  protected val TextFieldsOmitNorms: Boolean = {
    if (Config.hasPath("lucenerdd.index.textfields.terms.omitnorms")) {
      Config.getBoolean("lucenerdd.index.textfields.terms.omitnorms")
    }
    else true
  }

  protected val TextFieldsIndexOptions: IndexOptions = {
    if (Config.hasPath("lucenerdd.index.textfields.options")) {
      val indexOptions = Config.getString("lucenerdd.index.textfields.options")

      indexOptions.toLowerCase match {
        case "docs" => IndexOptions.DOCS
        case "docs_and_freqs" => IndexOptions.DOCS_AND_FREQS
        case "docs_and_freqs_and_positions" => IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
        case "docs_and_freqs_and_positions_and_offsets" =>
          IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
        case "none" => IndexOptions.NONE
      }
    }
    else IndexOptions.DOCS_AND_FREQS  // Default
  }


}
