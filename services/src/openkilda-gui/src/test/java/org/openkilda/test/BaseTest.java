package org.openkilda.test;

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

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.openkilda.util.IConstantsTest;
import org.openkilda.utility.IoUtil;

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
        } catch (Exception exception) {
            LOGGER.error(
                    "exception occured Inside method executeKildaFiles: " + exception.getMessage());
        }

        for (String url : urlList) {
            try {
                String[] inputValue = url.split("/");
                String FileName = inputValue[inputValue.length - 1];

                if (url.contains(".css")) {
                    downloadFiles(url,
                            IConstantsTest.CLASSPATH + IConstantsTest.CSS_PATH + FileName);
                    Assert.assertTrue(true);
                }
                if (url.contains(".js")) {
                    if (FileName.contains(IConstantsTest.JQUERY_FILE)) {
                        FileName = IConstantsTest.JQUERY_MIN_FILE;
                    }
                    downloadFiles(url,
                            IConstantsTest.CLASSPATH + IConstantsTest.JAVASCRIPT_PATH + FileName);
                    Assert.assertTrue(true);
                }
                if (url.contains("ttf") || url.contains("woff2") || url.contains("woff")) {
                    downloadFiles(url,
                            IConstantsTest.CLASSPATH + IConstantsTest.FONTS_PATH + FileName);
                    Assert.assertTrue(true);
                }
                if (url.contains("Roboto")) {
                    downloadFiles(url,
                            IConstantsTest.CLASSPATH + IConstantsTest.CSS_PATH + "roboto.css");
                    Assert.assertTrue(true);
                }

            } catch (Exception exception) {
                LOGGER.error("exception occured Inside method executeKildaFiles : "
                        + exception.getMessage());
                Assert.assertTrue(false);
            }
        }
        LOGGER.info("executeKildaFiles has been successfully executed");

    }

    /**
     * Download files.
     *
     * @param urlStr the url str
     * @param file the file
     */
    private void downloadFiles(final String urlStr, final String file) {
        if (file.contains(IConstantsTest.FONTS_PATH)) {
            File directory = new File(IConstantsTest.CLASSPATH + IConstantsTest.FONTS_PATH);
            if (!directory.exists()) {
                directory.mkdir();
            }
        }
        if (file.contains(IConstantsTest.JAVASCRIPT_PATH)) {
            File directory = new File(IConstantsTest.CLASSPATH + IConstantsTest.JAVASCRIPT_PATH);
            if (!directory.exists()) {
                directory.mkdir();
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

        } catch (MalformedURLException malformedURLException) {
            LOGGER.error("exception occured during accessing file url" + urlStr + " : exception : "
                    + malformedURLException.getMessage());
        } catch (IOException ioException) {
            LOGGER.error("exception occured during downloading file " + file + " : exception : "
                    + ioException.getMessage());
        } catch (Exception exception) {
            LOGGER.error("exception occured during downloading file " + file + " : exception : "
                    + exception.getMessage());
        } finally {
            IoUtil.close(fileOutputStream);
            IoUtil.close(bufferedInputStream);
        }
    }
}
