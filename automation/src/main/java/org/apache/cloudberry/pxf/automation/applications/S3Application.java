package org.apache.cloudberry.pxf.automation.applications;

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

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.cloudberry.pxf.automation.testcontainers.MinIOContainer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * S3 API access wrapper used by automation tests to seed and clean fixtures
 * in a MinIO bucket. Owns the AmazonS3 client; callers should call
 * `shutdown()` when done (typically in afterClass before stopping the
 * MinIO container).
 */
public class S3Application implements AutoCloseable {

    private final AmazonS3 s3Client;

    public S3Application(MinIOContainer minio) {
        this.s3Client = buildS3Client(minio.getHostEndpoint(), minio.getAccessKey(), minio.getSecretKey());
    }

    public void createBucket(String bucket) {
        if (!s3Client.doesBucketExistV2(bucket)) {
            s3Client.createBucket(bucket);
        }
    }

    public void putObject(String bucket, String key, Path localFile) throws IOException {
        s3Client.putObject(new PutObjectRequest(bucket, key, localFile.toFile()));
    }

    public void deletePrefix(String bucket, String prefix) {
        ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(bucket)
                .withPrefix(prefix);
        ListObjectsV2Result listing;
        do {
            listing = s3Client.listObjectsV2(request);
            List<String> keys = new ArrayList<>();
            for (S3ObjectSummary summary : listing.getObjectSummaries()) {
                keys.add(summary.getKey());
            }
            if (!keys.isEmpty()) {
                s3Client.deleteObjects(new DeleteObjectsRequest(bucket).withKeys(keys.toArray(new String[0])));
            }
            request.setContinuationToken(listing.getNextContinuationToken());
        } while (listing.isTruncated());
    }

    public void shutdown() {
        s3Client.shutdown();
    }

    @Override
    public void close() {
        shutdown();
    }

    private static AmazonS3 buildS3Client(String endpoint, String accessKey, String secretKey) {
        return AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, "us-east-1"))
                .withPathStyleAccessEnabled(true)
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(accessKey, secretKey)))
                .build();
    }
}
