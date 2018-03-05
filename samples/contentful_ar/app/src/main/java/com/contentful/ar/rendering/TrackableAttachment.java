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
package com.contentful.ar.rendering;

import com.google.ar.core.Anchor;
import com.google.ar.core.Pose;
import com.google.ar.core.Trackable;

import static com.google.ar.core.TrackingState.TRACKING;

/**
 * This class tracks the attachment of object's Anchor to a Plane. It will construct a pose
 * that will stay on the plane (in Y direction), while still properly tracking the XZ changes
 * from the anchor updates.
 */
public class TrackableAttachment {
  private final Trackable mTrackable;
  private final Anchor mAnchor;

  // Allocate temporary storage to avoid multiple allocations per frame.
  private final float[] mPoseTranslation = new float[3];
  private final float[] mPoseRotation = new float[4];

  public TrackableAttachment(Trackable trackable, Anchor anchor) {
    mTrackable = trackable;
    mAnchor = anchor;
  }

  public boolean isTracking() {
    return /*true if*/
        mTrackable.getTrackingState() == TRACKING && mAnchor.getTrackingState() == TRACKING;
  }

  public Pose getPose() {
    Pose pose = mAnchor.getPose();
    pose.getTranslation(mPoseTranslation, 0);
    pose.getRotationQuaternion(mPoseRotation, 0);
    mPoseTranslation[1] = mTrackable.getAnchors().iterator().next().getPose().ty();
    return new Pose(mPoseTranslation, mPoseRotation);
  }

  public Anchor getAnchor() {
    return mAnchor;
  }
}
