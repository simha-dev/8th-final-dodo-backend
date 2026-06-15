package com.depromeet.dodo.common.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import com.depromeet.dodo.common.dto.S3UploadImage;
import com.depromeet.dodo.common.exception.AwsS3SaveFailedException;
import com.depromeet.dodo.config.AWSConfig;
import com.depromeet.dodo.image.domain.Image;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

	private final AWSConfig awsConfig;

	@Value("${cloud.aws.s3.bucket}")
	private String bucket;

	@SneakyThrows
	public String uploadFile(MultipartFile file, String fileName) {
		S3Client s3Client = awsConfig.AwsS3Client();

		long contentLength = file.getSize();
		PutObjectRequest putObjectRequest = PutObjectRequest.builder()
			.bucket(bucket)
			.key(fileName)
			.contentLength(contentLength)
			.acl(ObjectCannedACL.PUBLIC_READ)
			.build();

		try {
			s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), contentLength));
		} catch (S3Exception | IOException e) {
			throw new AwsS3SaveFailedException(e);
		}

		return getUrl(bucket, fileName);
	}

	public String getUrl(String path, String fileName) {
		S3Client s3Client = awsConfig.AwsS3Client();
		GetUrlRequest getUrlRequest = GetUrlRequest.builder()
			.bucket(path)
			.key(fileName)
			.build();
		URL url = s3Client.utilities().getUrl(getUrlRequest);
		return url.toString();
	}

	public List<S3UploadImage> uploadFiles(String folderName, List<S3UploadImage> s3UploadImages) {
		List<File> files = new ArrayList<>();
		s3UploadImages.stream()
			.forEach(x -> files.add(convert(x.getProfileImage().getImageFile(), x.getFileName())
				.orElseThrow(() -> new IllegalArgumentException("MultipartFile -> File로 전환이 실패했습니다."))));

		List<String> filePaths = s3UploadFileList(folderName, files);
		filePaths.stream().forEach(x -> s3UploadImages.get(filePaths.indexOf(x)).setFilePath(x));
		return s3UploadImages;
	}

	@SneakyThrows
	private List<String> s3UploadFileList(String folderName, List<File> files) {
		S3Client s3Client = awsConfig.AwsS3Client();

		try {
			for (File file : files) {
				String key = folderName + "/" + file.getName();
				PutObjectRequest putObjectRequest = PutObjectRequest.builder()
					.bucket(bucket)
					.key(key)
					.acl(ObjectCannedACL.PUBLIC_READ)
					.build();
				s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
			}
		} catch (S3Exception e) {
			throw new AwsS3SaveFailedException(e);
		} finally {
			files.stream().forEach(x -> x.delete());
		}

		List<String> filesUrl = new ArrayList<>();
		files.stream().forEach(x -> filesUrl.add(getUrl(bucket, folderName + "/" + x.getName())));
		return filesUrl;
	}

	@SneakyThrows
	private Optional<File> convert(MultipartFile file, String fileName) {
		File convertFile = new File(fileName);
		try {
			if (convertFile.createNewFile()) {
				try (FileOutputStream fos = new FileOutputStream(convertFile)) {
					fos.write(file.getBytes());
				}
				return Optional.of(convertFile);
			}
		} catch (IOException e) {
			throw new AwsS3SaveFailedException("MultipartFile -> File로 전환이 실패했습니다.", e);
		}

		return Optional.empty();
	}

	public void deleteS3Object(String path, Image image) {
		S3Client s3Client = awsConfig.AwsS3Client();
		DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
			.bucket(path)
			.key(image.getFileName())
			.build();
		s3Client.deleteObject(deleteObjectRequest);
	}

}
