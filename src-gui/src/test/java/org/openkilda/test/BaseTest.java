/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.test;

import org.openkilda.util.IConstantsTest;
import org.openkilda.utility.IoUtil;

import org.apache.log4j.Logger;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * The Class BaseTest.
 *
 * @author Gaurav Chugh
 */
public class BaseTest {

    private static final Logger LOGGER = Logger.getLogger(BaseTest.class);

    /**
     * Execute kilda files.
     */
    @Test
    public void executeKildaFiles() {
        LOGGER.info("Inside method executeKildaFiles");
        File f = new File(IConstantsTest.FILE_PATH);
        List<String> urlList = new ArrayList<String>();
        String readLine = "";

        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(f))) {
            while ((readLine = bufferedReader.readLine()) != null) {
                urlList.add(readLine);
            }
        } catch (Exception e) {
            LOGGER.error("exception occurred Inside method executeKildaFiles", e);
        }

        for (String url : urlList) {
            try {
                String[] inputValue = url.split("/");
                String fileName = inputValue[inputValue.length - 1];

                if (url.contains(".css")) {
                    downloadFiles(url, IConstantsTest.CLASSPATH + IConstantsTest.CSS_PATH + fileName);
                    Assert.assertTrue(true);
                }
                if (url.contains(".js")) {
                    if (fileName.contains(IConstantsTest.JQUERY_FILE)) {
                        fileName = IConstantsTest.JQUERY_MIN_FILE;
                    }
                    downloadFiles(url, IConstantsTest.CLASSPATH + IConstantsTest.JAVASCRIPT_PATH + fileName);
                    Assert.assertTrue(true);
                }
                if (url.contains("ttf") || url.contains("woff2") || url.contains("woff")) {
                    downloadFiles(url, IConstantsTest.CLASSPATH + IConstantsTest.FONTS_PATH + fileName);
                    Assert.assertTrue(true);
                }
                if (url.contains("Roboto")) {
                    downloadFiles(url, IConstantsTest.CLASSPATH + IConstantsTest.CSS_PATH + "roboto.css");
                    Assert.assertTrue(true);
                }

            } catch (Exception e) {
                LOGGER.error("exception occurred Inside method executeKildaFiles.", e);
                Assert.assertTrue(false);
            }
        }
        LOGGER.info("executeKildaFiles has been successfully executed");

    }

    /**
     * Download files.
     *
     * @param urlStr
     *            the url str
     * @param file
     *            the file
     */
    private void downloadFiles(final String urlStr, final String file) {
        if (file.contains(IConstantsTest.FONTS_PATH)) {
            File directory = new File(IConstantsTest.CLASSPATH + IConstantsTest.FONTS_PATH);
            if (!directory.exists()) {
                directory.mkdirs();
            }
        }
        if (file.contains(IConstantsTest.JAVASCRIPT_PATH)) {
            File directory = new File(IConstantsTest.CLASSPATH + IConstantsTest.JAVASCRIPT_PATH);
            if (!directory.exists()) {
                directory.mkdirs();
            }
        }
        if (file.contains(IConstantsTest.CSS_PATH)) {
            File directory = new File(IConstantsTest.CLASSPATH + IConstantsTest.CSS_PATH);
            if (!directory.exists()) {
                directory.mkdirs();
            }
        }

        File fileObject = new File(file);
        if (fileObject.exists()) {
            return;
        }

        LOGGER.info("Downloading file " + file);
        URL url = null;
        FileOutputStream fileOutputStream = null;
        BufferedInputStream bufferedInputStream = null;

        try {
            url = new URL(urlStr);
            bufferedInputStream = new BufferedInputStream(url.openStream());
            fileOutputStream = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int count = 0;

            while ((count = bufferedInputStream.read(buffer, 0, 1024)) != -1) {
                fileOutputStream.write(buffer, 0, count);
            }

        } catch (MalformedURLException malformedUrlException) {
            LOGGER.error("Error occurred during accessing file url" + urlStr + " : exception : "
                    + malformedUrlException.getMessage());
        } catch (IOException ioException) {
            LOGGER.error(
                    "Error occurred during downloading file " + file + " : exception : " + ioException.getMessage());
        } catch (Exception exception) {
            LOGGER.error(
                    "Error occurred during downloading file " + file + " : exception : " + exception.getMessage());
        } finally {
            IoUtil.close(fileOutputStream);
            IoUtil.close(bufferedInputStream);
        }
    }
}
