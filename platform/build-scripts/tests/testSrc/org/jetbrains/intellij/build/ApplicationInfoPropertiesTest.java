// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;

public class ApplicationInfoPropertiesTest {
  @Test
  public void majorReleaseDate() {
    assertEquals("20200401", ApplicationInfoPropertiesImplKt.formatMajorReleaseDate("20200401"));
    assertEquals("20200401", ApplicationInfoPropertiesImplKt.formatMajorReleaseDate("202004012323"));
  }

  @Test
  public void majorReleaseDateGenerated() {
    var now = System.currentTimeMillis() / 1000;
    var expectedDate = ZonedDateTime.ofInstant(Instant.ofEpochSecond(now), ZoneOffset.UTC)
      .format(ApplicationInfoPropertiesImplKt.getMAJOR_RELEASE_DATE_PATTERN());
    assertEquals(expectedDate, ApplicationInfoPropertiesImplKt.formatMajorReleaseDate(null, now));
    assertEquals(expectedDate, ApplicationInfoPropertiesImplKt.formatMajorReleaseDate("__BUILD_DATE__", now));
  }

  // phantom-idea: phantom-build.sh's display version replaces the placeholder of the tracked IdeaApplicationInfo.xml
  @Test
  public void phantomVersion() {
    var source = "<component xmlns=\"http://jetbrains.org/intellij/schema/application-info\">\n" +
                 "  <version eap=\"false\" major=\"0\" minor=\"0.0\"/> <!-- phantom-idea: placeholder, -Dphantom.version.major/minor -->\n" +
                 "  <build number=\"IC-262.261112\" date=\"202611120000\" majorReleaseDate=\"20260716\"/>\n" +
                 "</component>";
    var expected = "<component xmlns=\"http://jetbrains.org/intellij/schema/application-info\">\n" +
                   "  <version eap=\"false\" major=\"26\" minor=\"11.12\"/> <!-- phantom-idea: placeholder, -Dphantom.version.major/minor -->\n" +
                   "  <build number=\"IC-262.261112\" date=\"202611120000\" majorReleaseDate=\"20260716\"/>\n" +
                   "</component>";
    assertEquals(expected, ApplicationInfoPropertiesImplKt.withPhantomVersion(source, "26", "11.12"));
  }

  @Test(expected = IllegalStateException.class)
  public void phantomVersionWithoutVersionElement() {
    ApplicationInfoPropertiesImplKt.withPhantomVersion("<component><build number=\"IC-1\"/></component>", "26", "11.12");
  }
}