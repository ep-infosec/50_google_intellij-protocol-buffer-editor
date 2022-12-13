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
package com.google.devtools.intellij.protoeditor.lang.stub.type;

import com.google.devtools.intellij.protoeditor.lang.psi.PbFile;
import com.google.devtools.intellij.protoeditor.lang.stub.PbFileStub;
import com.intellij.lang.Language;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.stubs.DefaultStubBuilder;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IStubFileElementType;
import java.io.IOException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PbFileElementType extends IStubFileElementType<PbFileStub> {
  public PbFileElementType(final Language language) {
    super(language);
  }

  public PbFileElementType(@NonNls final String debugName, final Language language) {
    super(debugName, language);
  }

  @Override
  public int getStubVersion() {
    return 0;
  }

  @NotNull
  @Override
  public StubBuilder getBuilder() {
    return new PbStubBuilder();
  }

  @NotNull
  @Override
  public String getExternalId() {
    return "protobuf.file";
  }

  @Override
  public void serialize(@NotNull final PbFileStub stub, @NotNull final StubOutputStream dataStream)
      throws IOException {}

  @NotNull
  @Override
  public PbFileStub deserialize(
      @NotNull final StubInputStream dataStream,
      final @SuppressWarnings("rawtypes") StubElement parentStub)
      throws IOException {
    return new PbFileStub(null);
  }

  @Override
  public void indexStub(@NotNull final PsiFileStub stub, @NotNull final IndexSink sink) {}

  private static class PbStubBuilder extends DefaultStubBuilder {
    @NotNull
    @Override
    protected @SuppressWarnings("rawtypes") StubElement createStubForFile(@NotNull PsiFile file) {
      return new PbFileStub((PbFile) file);
    }
  }
}
