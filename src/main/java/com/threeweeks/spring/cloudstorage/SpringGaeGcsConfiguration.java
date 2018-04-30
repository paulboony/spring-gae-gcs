package com.threeweeks.spring.cloudstorage;

import com.google.api.client.extensions.appengine.http.UrlFetchTransport;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.extensions.appengine.auth.oauth2.AppIdentityCredential;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.threeweeks.spring.cloudstorage.apiclient.GcsJsonApiClient;
import com.threeweeks.spring.cloudstorage.apiclient.LocalGcsJsonApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static java.util.Collections.singletonList;

@Configuration
public class SpringGaeGcsConfiguration {
    //TODO-AC: This should be a configurable property.
    private static final String DEV_CREDENTIALS_FILE = "/dev-gcs-credentials.json";
    private static final List<String> STORAGE_SCOPES = singletonList("https://www.googleapis.com/auth/devstorage.full_control");
    private static final Logger LOGGER = LoggerFactory.getLogger(SpringGaeGcsConfiguration.class);


    @Bean
    @ConditionalOnMissingBean(HttpTransport.class)
    public HttpTransport getHttpTransport() {
        LOGGER.info("HttpTransport bean not found. Default UrlFetchTransport.getDefaultInstance()");
        return UrlFetchTransport.getDefaultInstance();
    }

    @Bean
    @ConditionalOnMissingBean(JsonFactory.class)
    public JsonFactory getJsonFactory() {
        LOGGER.info("JSON factory bean not found, default new GsonFactory()");
        return new GsonFactory();
    }

    @Profile("gae")
    @Bean
    public GcsJsonApiClient getGaeGcsClient(HttpTransport httpTransport, JsonFactory jsonFactory) {

        GoogleCredential googleCredential = getGaeGoogleCredential(httpTransport, jsonFactory);

        return new GcsJsonApiClient(getHttpRequestFactory(googleCredential, httpTransport),
                getAppIdentityService(), googleCredential);
    }

    @Profile("!gae")
    @Bean
    public GcsJsonApiClient getLocalGcsClient(HttpTransport httpTransport, JsonFactory jsonFactory) {

        GoogleCredential googleCredential = getLocalDevGoogleCredential(httpTransport, jsonFactory);

        return new LocalGcsJsonApiClient(getHttpRequestFactory(googleCredential, httpTransport),
                getAppIdentityService(), googleCredential);
    }

    @Bean
    public CloudStorageService getCloudStorageService(GcsJsonApiClient cloudStorage,
                                                      @Value("${gcs.defaultbucket}") String gcsDefaultBucket,
                                                      @Value("${app.host}") String host,
                                                      @Value("#{'${gcs.attachmentFolders:attachments}'.split(',')}")
                                                              List<String> gcsAttachmentFolders) {
        return new CloudStorageService(cloudStorage, gcsDefaultBucket, host, gcsAttachmentFolders, "attachments");
    }

    private GoogleCredential getLocalDevGoogleCredential(HttpTransport httpTransport, JsonFactory jsonFactory) {

        try (InputStream jsonCredentials = getClass().getResourceAsStream(DEV_CREDENTIALS_FILE)) {
            return GoogleCredential
                    .fromStream(jsonCredentials, httpTransport, jsonFactory)
                    .createScoped(STORAGE_SCOPES);
        } catch (IOException e) {
            String msg = String.format("Cloud storage client configuration failed. Ensure you have a local credentials file created in src/main/resources/%s." +
                            "See https://developers.google.com/identity/protocols/application-default-credentials. Alternatively you can remove " +
                            "this %s from your ApplicationModule if you do not require Google Cloud Storage (e.g. file uploads).",
                    DEV_CREDENTIALS_FILE, getClass().getSimpleName());
            throw new RuntimeException(msg, e);
        }
    }

    private GoogleCredential getGaeGoogleCredential(HttpTransport httpTransport, JsonFactory jsonFactory) {
        try {
            return new AppIdentityCredential.AppEngineCredentialWrapper(httpTransport, jsonFactory)
                    .createScoped(STORAGE_SCOPES);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Cloud storage client configuration failed: %s", e.getMessage()), e);
        }
    }

    private AppIdentityService getAppIdentityService() {
        return AppIdentityServiceFactory.getAppIdentityService();
    }

    private HttpRequestFactory getHttpRequestFactory(GoogleCredential googleCredential, HttpTransport httpTransport) {
        HttpRequestInitializer requestInitializer = googleCredential;
        return httpTransport.createRequestFactory(requestInitializer);
    }

}