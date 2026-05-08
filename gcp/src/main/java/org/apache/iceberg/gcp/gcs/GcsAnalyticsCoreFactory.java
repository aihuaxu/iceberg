/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg.gcp.gcs;

import com.google.auth.Credentials;
import com.google.cloud.gcs.analyticscore.client.GcsFileInfo;
import com.google.cloud.gcs.analyticscore.client.GcsFileSystem;
import com.google.cloud.gcs.analyticscore.client.GcsFileSystemImpl;
import com.google.cloud.gcs.analyticscore.client.GcsFileSystemOptions;
import com.google.cloud.gcs.analyticscore.client.GcsItemId;
import com.google.cloud.gcs.analyticscore.client.GcsItemInfo;
import com.google.cloud.gcs.analyticscore.core.GcsAnalyticsCoreOptions;
import com.google.cloud.gcs.analyticscore.core.GoogleCloudStorageInputStream;
import com.google.cloud.storage.BlobId;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;

/**
 * Factory that isolates all gcs-analytics-core library references. This class is only loaded by the
 * JVM when analytics core is actually enabled, avoiding NoClassDefFoundError for downstream
 * projects that do not include gcs-analytics-core on their classpath.
 */
class GcsAnalyticsCoreFactory implements AutoCloseable {
  private final GcsFileSystem gcsFileSystem;

  GcsAnalyticsCoreFactory(Credentials credentials, Map<String, String> properties) {
    GcsAnalyticsCoreOptions gcsAnalyticsCoreOptions =
        new GcsAnalyticsCoreOptions("gcs.", properties);
    GcsFileSystemOptions fileSystemOptions = gcsAnalyticsCoreOptions.getGcsFileSystemOptions();
    this.gcsFileSystem =
        credentials == null
            ? new GcsFileSystemImpl(fileSystemOptions)
            : new GcsFileSystemImpl(credentials, fileSystemOptions);
  }

  SeekableInputStream newInputStream(BlobId blobId, Long blobSize, MetricsContext metrics)
      throws IOException {
    if (blobSize == null) {
      return new GcsInputStreamWrapper(
          GoogleCloudStorageInputStream.create(gcsFileSystem, gcsItemId(blobId)), blobId, metrics);
    }

    return new GcsInputStreamWrapper(
        GoogleCloudStorageInputStream.create(gcsFileSystem, gcsFileInfo(blobId, blobSize)),
        blobId,
        metrics);
  }

  private static GcsItemId gcsItemId(BlobId blobId) {
    GcsItemId.Builder builder =
        GcsItemId.builder().setBucketName(blobId.getBucket()).setObjectName(blobId.getName());
    if (blobId.getGeneration() != null) {
      builder.setContentGeneration(blobId.getGeneration());
    }

    return builder.build();
  }

  private static GcsFileInfo gcsFileInfo(BlobId blobId, long size) {
    GcsItemId itemId = gcsItemId(blobId);
    GcsItemInfo itemInfo = GcsItemInfo.builder().setItemId(itemId).setSize(size).build();
    return GcsFileInfo.builder()
        .setItemInfo(itemInfo)
        .setUri(URI.create(blobId.toGsUtilUri()))
        .setAttributes(ImmutableMap.of())
        .build();
  }

  GcsFileSystem gcsFileSystem() {
    return gcsFileSystem;
  }

  @Override
  public void close() throws IOException {
    gcsFileSystem.close();
  }
}
