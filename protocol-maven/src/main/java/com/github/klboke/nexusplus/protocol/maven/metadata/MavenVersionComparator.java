package com.github.klboke.nexusplus.protocol.maven.metadata;

import java.util.Comparator;
import java.util.regex.Pattern;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Nexus-compatible string version comparator.
 *
 * <p>Nexus sorts Maven metadata versions by treating version-looking strings as Maven versions,
 * placing non-version strings first, then version strings in Maven order.
 */
public final class MavenVersionComparator implements Comparator<String> {
  public static final MavenVersionComparator INSTANCE = new MavenVersionComparator();

  private static final Pattern VERSION_RE =
      Pattern.compile("^\\d+([._-][0-9a-z]+)*$", Pattern.CASE_INSENSITIVE);

  private MavenVersionComparator() {}

  @Override
  public int compare(String left, String right) {
    if (left == null && right == null) return 0;
    if (left == null) return -1;
    if (right == null) return 1;
    boolean leftVersion = isVersionLike(left);
    boolean rightVersion = isVersionLike(right);
    if (leftVersion ^ rightVersion) {
      return leftVersion ? 1 : -1;
    }
    if (leftVersion) {
      return new ComparableVersion(left).compareTo(new ComparableVersion(right));
    }
    return left.compareTo(right);
  }

  private static boolean isVersionLike(String version) {
    return VERSION_RE.matcher(version).matches();
  }
}
