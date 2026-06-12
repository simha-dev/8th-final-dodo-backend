package com.depromeet.dodo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

@Configuration
public class AWSConfig {

	@Value("${cloud.aws.credentials.accessKey}")
	private String accessKey;

	@Value("${cloud.aws.credentials.secretKey}")
	private String secretKey;

	@Bean
	public StaticCredentialsProvider awsCredentialsProvider() {
		return StaticCredentialsProvider.create(
			AwsBasicCredentials.create(accessKey, secretKey));
	}

	@Bean
	public S3Client awsS3Client() {
		return S3Client.builder()
			.region(Region.AP_NORTHEAST_2)
			.credentialsProvider(awsCredentialsProvider())
			.build();
	}

	@Bean
	public S3TransferManager s3TransferManager() {
		return S3TransferManager.builder()
			.s3Client(software.amazon.awssdk.services.s3.S3AsyncClient.builder()
				.region(Region.AP_NORTHEAST_2)
				.credentialsProvider(awsCredentialsProvider())
				.build())
			.build();
	}

}
