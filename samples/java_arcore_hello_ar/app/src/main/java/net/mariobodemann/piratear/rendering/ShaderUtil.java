/*
 * Copyright 2017 Google Inc. All Rights Reserved.
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
package net.mariobodemann.piratear.rendering;

import android.opengl.GLES20;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Shader helper functions.
 */
public class ShaderUtil {

  /**
   * Converts a raw text file, into an OpenGL ES shader.
   *
   * @param tag  Log tag for error logging.
   * @param file the file to be loaded as a shader.
   * @param type The type of shader we will be creating.
   * @return The shader object handler.
   */
  public static int loadGLShader(String tag, String file, int type) {
    String code;
    try {
      code = readRawTextFile(new FileInputStream(file));
    } catch (FileNotFoundException e) {
      Log.e(tag, "Could not load shader file '" + file + "'.");
      return 0;
    }

    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, code);
    GLES20.glCompileShader(shader);

    // Get the compilation status.
    final int[] compileStatus = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

    // If the compilation failed, delete the shader.
    if (compileStatus[0] == 0) {
      Log.e(tag, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
      GLES20.glDeleteShader(shader);
      shader = 0;
    }

    if (shader == 0) {
      throw new RuntimeException("Error creating shader.");
    }

    return shader;
  }

  /**
   * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
   *
   * @param label Label to report in case of error.
   * @throws RuntimeException If an OpenGL error is detected.
   */
  public static void checkGLError(String tag, String label) {
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      Log.e(tag, label + ": glError " + error);
      throw new RuntimeException(label + ": glError " + error);
    }
  }

  /**
   * Converts a raw text file into a string.
   *
   * @param inputStream the stream to be read.
   * @return The context of the text file, or null in case of error.
   */
  public static String readRawTextFile(InputStream inputStream) {
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      reader.close();
      return sb.toString();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Normalize the filename, aka add a basepath if needed.
   */
  public static String normalizeFileName(String fileName, String basepath) {
    if (!fileName.startsWith("/")) {
      if (!basepath.endsWith("/")) {
        basepath += "/";
      }

      fileName = basepath + fileName;
    }
    return fileName;
  }


}
