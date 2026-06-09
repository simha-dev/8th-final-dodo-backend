package com.depromeet.dodo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AWSConfig {

	@Value("${cloud.aws.credentials.accessKey}")
	private String accessKey;

	@Value("${cloud.aws.credentials.secretKey}")
	private String secretKey;

	@Bean
	public StaticCredentialsProvider AwsCredentials() {
		return StaticCredentialsProvider.create(
			AwsBasicCredentials.create(accessKey, secretKey));
	}

	@Bean
	public S3Client AwsS3Client() {
		return S3Client.builder()
			.region(Region.AP_NORTHEAST_2)
			.credentialsProvider(AwsCredentials())
			.build();
	}

}
