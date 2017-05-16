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
package org.apache.spark.deploy.kubernetes.submit.v2

import com.google.common.base.Charsets
import com.google.common.io.{BaseEncoding, Files}
import io.fabric8.kubernetes.api.model.{Secret, SecretBuilder}
import scala.collection.JavaConverters._

import org.apache.spark.SSLOptions
import org.apache.spark.deploy.kubernetes.constants._

private[spark] trait SubmittedDependencySecretBuilder {
  /**
   * Construct a Kubernetes secret bundle that init-containers can use to retrieve an
   * application's dependencies.
   */
  def buildInitContainerSecret(): Secret
}

private[spark] class SubmittedDependencySecretBuilderImpl(
    secretName: String,
    jarsResourceSecret: String,
    filesResourceSecret: String,
    jarsSecretKey: String,
    filesSecretKey: String,
    trustStoreSecretKey: String,
    resourceStagingServerSslOptions: SSLOptions)
    extends SubmittedDependencySecretBuilder {

  override def buildInitContainerSecret(): Secret = {
    val trustStoreBase64 = resourceStagingServerSslOptions.trustStore.map { trustStoreFile =>
      require(trustStoreFile.isFile, "Dependency server trustStore provided at" +
        trustStoreFile.getAbsolutePath + " does not exist or is not a file.")
      (trustStoreSecretKey, BaseEncoding.base64().encode(Files.toByteArray(trustStoreFile)))
    }.toMap
    val jarsSecretBase64 = BaseEncoding.base64().encode(jarsResourceSecret.getBytes(Charsets.UTF_8))
    val filesSecretBase64 = BaseEncoding.base64().encode(
      filesResourceSecret.getBytes(Charsets.UTF_8))
    val secretData = Map(
      jarsSecretKey -> jarsSecretBase64,
      filesSecretKey -> filesSecretBase64) ++
      trustStoreBase64
    val kubernetesSecret = new SecretBuilder()
      .withNewMetadata()
      .withName(secretName)
      .endMetadata()
      .addToData(secretData.asJava)
      .build()
    kubernetesSecret
  }
}