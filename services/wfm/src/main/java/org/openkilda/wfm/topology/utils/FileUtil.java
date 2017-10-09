/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.topology.utils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * Simple File Utility class .. centered around appending and counting and things like that.
 */
public class FileUtil implements Serializable {

    public File dir = Files.createTempDir();
    public String fileName = "temp.txt";
    private File file;

    public FileUtil withDir(File dir) {
        this.dir = dir;
        return this;
    }

    public FileUtil withFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public File getFile() {
        if (file == null) {
            dir.mkdirs();
            file = new File(dir.getAbsolutePath(), fileName);
        }
        return file;
    }

    /**
     * Append the text. If an exception occurs, dump the text and return false.
     *
     * @return true if append worked, false otherwise.
     */
    public boolean append(String text) {
        boolean success = true;

        try {
            Files.append(text, getFile(), Charsets.UTF_8);
        } catch (IOException e) {
            success = false;
            e.printStackTrace();
        }

        return success;
    }

    /**
     * @return the actual number, or -1 if there was an exception.
     */
    public int numLines() {
        int result = -1;

        try {
            result = Files.readLines(getFile(), Charsets.UTF_8).size();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }
}
