/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.rt.coverage.data;

/**
 * @author anna
 * @since 2/9/11
 */
public class FileMapData { 
  public static final FileMapData[] EMPTY_FILE_MAP = new FileMapData[0];
  private final String myClassName;
  private final String myFileName;
  private final LineMapData[] myLines;

  public FileMapData(String className, String fileName, LineMapData[] lines) {
    myClassName = className;
    myFileName = fileName;
    myLines = lines;
  }

  public String getClassName() {
    return myClassName;
  }

  public String getFileName() {
    return myFileName;
  }

  public LineMapData[] getLines() {
    return myLines;
  }

  public String toString() {
    StringBuilder toString = new StringBuilder();
    for (LineMapData line : myLines) {
      if (line != null) {
        toString.append("\n").append(line.toString());
      }
    }
    return "class name: " + myClassName + "\nlines:" + toString;
  }
}
