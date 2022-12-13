/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.devtools.intellij.protoeditor.java.names;

import com.google.devtools.intellij.protoeditor.java.PbJavaGotoDeclarationContext;
import com.google.devtools.intellij.protoeditor.lang.psi.PbEnumDefinition;
import com.google.devtools.intellij.protoeditor.lang.psi.PbEnumValue;
import com.google.devtools.intellij.protoeditor.lang.psi.PbField;
import com.google.devtools.intellij.protoeditor.lang.psi.PbFile;
import com.google.devtools.intellij.protoeditor.lang.psi.PbMessageType;
import com.google.devtools.intellij.protoeditor.lang.psi.PbOneofDefinition;
import com.google.devtools.intellij.protoeditor.lang.psi.PbTypeName;
import com.google.devtools.intellij.protoeditor.lang.psi.util.PbPsiUtil;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Represents names for a particular {@link PbFile}. Given an element of the file, returns the set
 * Java of names that are generated by protoc.
 */
public interface NameGenerator {

  /** Returns all of the possible outer classes generated from the associated {@link PbFile} */
  Set<String> outerClassNames();

  /**
   * Returns all possible fully-qualified class names generated for a message. Includes additional
   * interfaces like FooOrBuilder.
   */
  Set<String> messageClassNames(PbMessageType messageType);

  /** Returns the class-relative members generated from a field */
  Set<String> fieldMemberNames(PbField field);

  /** Returns the fully-qualified java enum name generated for a proto enum */
  @Nullable
  String enumClassName(PbEnumDefinition enumDefinition);

  /** Returns the java enum constant name generated for a proto enum value */
  @Nullable
  String enumValueName(PbEnumValue enumValue);

  /**
   * Returns the set of java message class members generated for a oneof definition. To get all java
   * names derived from a oneof definition name, see also {@link #oneofEnumClassName}, and {@link
   * #oneofNotSetEnumValueName}.
   */
  Set<String> oneofMemberNames(PbOneofDefinition oneof);

  /**
   * Returns the fully-qualified java enum definition name generated for a proto oneof definition
   */
  @Nullable
  String oneofEnumClassName(PbOneofDefinition oneof);

  /**
   * Returns the java enum constant name generated from a proto oneof definition for indicating that
   * the oneof field isn't set yet.
   */
  @Nullable
  String oneofNotSetEnumValueName(PbOneofDefinition oneof);

  /**
   * Returns the java enum constant name generated for a proto field within a oneof definition. To
   * get all names related to this field, also check {@link #fieldMemberNames}.
   */
  @Nullable
  String oneofEnumValueName(PbField oneofField);

  /** Convert this generator into a matcher, provided a context to match */
  NameMatcher toNameMatcher(PbJavaGotoDeclarationContext context);

  /** Return a normalized field name, used for the Java naming scheme. */
  static String fieldName(PbField field) {
    // See special treatment for group fields:
    // https://github.com/google/protobuf/blob/3.0.x/src/google/protobuf/compiler/java/java_helpers.cc#L83
    PbTypeName typeName = field.getTypeName();
    if (typeName != null && PbPsiUtil.fieldIsGroup(field)) {
      return typeName.getShortName();
    }
    return field.getName();
  }
}
