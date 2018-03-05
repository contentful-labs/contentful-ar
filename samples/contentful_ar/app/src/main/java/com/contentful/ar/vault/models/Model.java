package com.contentful.ar.vault.models;

import com.contentful.vault.Asset;
import com.contentful.vault.ContentType;
import com.contentful.vault.Field;
import com.contentful.vault.Resource;

@ContentType("model")
public class Model extends Resource {
  @Field
  String title;

  @Field
  String description;

  @Field
  Asset model;

  @Field
  Asset texture;

  @Field
  Double scale;

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  public Asset model() {
    return model;
  }

  public Asset texture() {
    return texture;
  }

  public Double scale() {
    return scale;
  }
}
