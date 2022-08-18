/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.solutions.bqremoteencryptionfn;

import static com.google.cloud.solutions.bqremoteencryptionfn.testing.JsonMapper.fromJson;
import static com.google.cloud.solutions.bqremoteencryptionfn.testing.SimpleBigQueryRemoteFnRequestMaker.testRequest;
import static com.google.common.truth.Truth.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.google.cloud.dlp.v2.DlpServiceClient;
import com.google.cloud.solutions.bqremoteencryptionfn.fns.DlpFn.DlpClientFactory;
import com.google.cloud.solutions.bqremoteencryptionfn.testing.stubs.dlp.Base64EncodingDlpStub;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@RunWith(Parameterized.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(BqTokenizeFnAppTest.TestDlpClientFactoryConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
public final class BqTokenizeFnAppTest {

  static {
    System.setProperty("AES_KEY", "2lDNBd0hHgCZ+1/P+fWO+g==");
    System.setProperty("AES_IV_PARAMETER_BASE64", "/t2/6YFewDgoHeQM1QBZdw==");
  }

  @ClassRule public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();
  @Rule public final SpringMethodRule springMethodRule = new SpringMethodRule();
  @Autowired MockMvc mockMvc;

  private final String testRequestJson;
  private final BigQueryRemoteFnResponse expectedResult;

  public BqTokenizeFnAppTest(
      String testCaseName, String testRequestJson, BigQueryRemoteFnResponse expectedResult) {
    this.testRequestJson = testRequestJson;
    this.expectedResult = expectedResult;
  }

  @Test
  public void operation_valid() throws Exception {
    mockMvc
        .perform(post("/").contentType("application/json").content(testRequestJson))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(
            result ->
                assertThat(
                        fromJson(
                            result.getResponse().getContentAsString(),
                            BigQueryRemoteFnResponse.class))
                    .isEqualTo(expectedResult));
  }

  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> testParameters() {
    return ImmutableList.of(
        new Object[] {
          /*testName=*/ "No-Op Tokenize",
          /*testRequestJson=*/ testRequest(
              Map.of("mode", "tokenize", "algo", "identity"), List.of("Anant"), List.of("Damle")),
          /*expectedResult=*/ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null)
        },
        new Object[] {
          /*testName=*/ "Identity ReIdenitfy",
          /*testRequestJson=*/ testRequest(
              Map.of("mode", "reidentify", "algo", "identity"), List.of("Anant"), List.of("Damle")),
          /*expectedResult=*/ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null)
        },
        new Object[] {
          /*testName=*/ "Base64 Tokenize",
          /*testRequestJson=*/ testRequest(
              Map.of("mode", "tokenize", "algo", "base64"), List.of("Anant"), List.of("Damle")),
          /*expectedResult=*/ new BigQueryRemoteFnResponse(List.of("QW5hbnQ=", "RGFtbGU="), null)
        },
        new Object[] {
          /*testName=*/ "Base64 ReIdentify",
          /*testRequestJson=*/ testRequest(
              Map.of("mode", "reidentify", "algo", "base64"),
              List.of("QW5hbnQ="),
              List.of("RGFtbGU=")),
          /*expectedResult=*/ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null)
        },
        new Object[] {
          /*testName=*/ "AES128-ECB Tokenize",
          /*testRequestJson=*/ testRequest(
              Map.of(
                  "mode", "tokenize",
                  "algo", "aes",
                  "aes-cipher-type", "AES/ECB/PKCS5PADDING"),
              List.of("Anant"),
              List.of("Damle")),
          /*expectedResult=*/ new BigQueryRemoteFnResponse(
              List.of("nrUwN61laFc115jyyQHmng==", "JCKtXkM8spJLyZdAqZKf/g=="), null)
        },
        new Object[] {
          /*testName=*/ "AES128-ECB ReIdentify",
          /*testRequestJson=*/ testRequest(
              Map.of(
                  "mode", "reidentify", "algo", "aes", "aes-cipher-type", "AES/ECB/PKCS5PADDING"),
              List.of("nrUwN61laFc115jyyQHmng=="),
              List.of("JCKtXkM8spJLyZdAqZKf/g==")),
          /*expectedResult=*/ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null)
        },
        new Object[] {
          /*testName=*/ "AES128 (default: CBC) Tokenize",
          /*testRequestJson=*/ testRequest(
              Map.of("mode", "tokenize", "algo", "aes"), List.of("Anant"), List.of("Damle")),
          /*expectedResult=*/ new BigQueryRemoteFnResponse(
              List.of("VhxcfvLBLRy8ag4DVl+7yQ==", "vjVNUHd2cpR0S8XLqhR+VQ=="), null)
        },
        new Object[] {
          /*testName=*/ "AES128 (default: CBC) pro ReIdentify",
          /*testRequestJson=*/ testRequest(
              Map.of("mode", "reidentify", "algo", "aes"),
              List.of("VhxcfvLBLRy8ag4DVl+7yQ=="),
              List.of("vjVNUHd2cpR0S8XLqhR+VQ==")),
          /*expectedResult=*/ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null)
        },
        new Object[] {
          /*testName=*/ "AES128 CBC User provided ivParameter Tokenize",
          /*testRequestJson=*/ testRequest(
              Map.of(
                  "mode",
                  "tokenize",
                  "algo",
                  "aes",
                  "aes-iv-parameter-base64",
                  "VGhpc0lzVGVzdFZlY3Rvcg=="),
              List.of("Anant"),
              List.of("Damle")),
          /*expectedResult=*/ new BigQueryRemoteFnResponse(
              List.of("8MWXmtCTjwOlpBopOGQZfg==", "m3XXwCieBwdWi700D9yZdg=="), null)
        },
        new Object[] {
          /*testName=*/ "AES128 CBC User provided ivParameter ReIdentify",
          /*testRequestJson=*/ testRequest(
              Map.of(
                  "mode",
                  "reidentify",
                  "algo",
                  "aes",
                  "aes-iv-parameter-base64",
                  "VGhpc0lzVGVzdFZlY3Rvcg=="),
              List.of("8MWXmtCTjwOlpBopOGQZfg=="),
              List.of("m3XXwCieBwdWi700D9yZdg==")),
          /*expectedResult=*/ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null)
        },
        new Object[] {
          /*testName=*/ "DLP tokenize",
          /*testRequestJson=*/ testRequest(
              Map.of(
                  "mode",
                  "tokenize",
                  "algo",
                  "dlp",
                  "dlp-deid-template",
                  "projects/test-project-id/locations/test-region1/deidentifyTemplates/template1"),
              List.of("Anant"),
              List.of("Damle")),
          /*expectedResult=*/ new BigQueryRemoteFnResponse(List.of("QW5hbnQ=", "RGFtbGU="), null)
        },
        new Object[] {
          /*testName=*/ "DLP reidentify",
          /*testRequestJson=*/ testRequest(
              Map.of(
                  "mode",
                  "reidentify",
                  "algo",
                  "dlp",
                  "dlp-deid-template",
                  "projects/test-project-id/locations/test-region1/deidentifyTemplates/template1"),
              List.of("QW5hbnQ="),
              List.of("RGFtbGU=")),
          /*expectedResult=*/ new BigQueryRemoteFnResponse(List.of("Anant", "Damle"), null)
        });
  }

  @TestConfiguration
  @Profile("test")
  public static class TestDlpClientFactoryConfiguration {

    @Bean
    public DlpClientFactory base64DlpClientFactory() {
      return () ->
          DlpServiceClient.create(
              new Base64EncodingDlpStub(
                  ImmutableSet.of("bqfnvalue"), "test-project-id", "test-region1"));
    }
  }
}
