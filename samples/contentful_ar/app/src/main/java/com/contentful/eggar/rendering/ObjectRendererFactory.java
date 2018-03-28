package com.contentful.eggar.rendering;


import java.io.File;

import static com.contentful.eggar.rendering.ShaderUtil.normalizeFileName;

public class ObjectRendererFactory {
  private static final String TAG = ObjectRendererFactory.class.getSimpleName();
  static final String DEFAULT_FRAGMENT_SHADER_FILE_NAME = "object_fragment.shader";
  static final String DEFAULT_VERTEX_SHADER_FILE_NAME = "object_vertex.shader";

  private final String basepath;

  public ObjectRendererFactory(String basepath) {
    if (!basepath.endsWith("/")) {
      basepath = basepath + "/";
    }

    this.basepath = basepath;
  }

  public ObjectRenderer create(String objectFileName) {
    objectFileName = normalizeFileName(objectFileName, basepath);
    return create(
        objectFileName,
        objectFileNameToTextureFileName(objectFileName));
  }

  public ObjectRenderer create(String objectFileName,
                               String textureFileName) {
    return create(
        objectFileName,
        textureFileName,
        normalizeFileName(DEFAULT_VERTEX_SHADER_FILE_NAME, basepath),
        normalizeFileName(DEFAULT_FRAGMENT_SHADER_FILE_NAME, basepath));
  }

  public ObjectRenderer create(String objectFileName,
                               String textureFileName,
                               String vertexShaderFileName,
                               String fragmentShaderFileName) {
    if (!checkExisting(objectFileName)
        || !checkExisting(textureFileName)
        || !checkExisting(vertexShaderFileName)
        || !checkExisting(fragmentShaderFileName)) {
      return null;
    } else {
      return new ObjectRenderer(objectFileName, textureFileName, fragmentShaderFileName, vertexShaderFileName);
    }
  }

  private String objectFileNameToTextureFileName(String fileName) {
    if (fileName.toLowerCase().endsWith(".obj")) {
      return fileName.substring(0, fileName.length() - 4).concat(".png");
    } else {
      return fileName.concat(".png");
    }
  }

  private boolean checkExisting(String fileName) {
    return new File(fileName).exists();
  }
}
