package com.github.klboke.nexusplus.server.goartifact;

import com.github.klboke.nexusplus.server.maven.MavenExceptions;
import com.github.klboke.nexusplus.server.maven.MavenResponse;
import com.github.klboke.nexusplus.server.maven.RepositoryRuntime;
import org.springframework.stereotype.Service;

@Service
public class GoGroupService {
  private final GoProxyService proxy;

  public GoGroupService(GoProxyService proxy) {
    this.proxy = proxy;
  }

  public MavenResponse get(RepositoryRuntime group, String rawPath, boolean headOnly) {
    if (!group.isGroup()) {
      throw new IllegalStateException("GoGroupService.get called on non-group " + group.name());
    }
    if (group.members().isEmpty()) {
      throw new MavenExceptions.MavenNotFoundException(rawPath);
    }
    for (RepositoryRuntime member : group.members()) {
      try {
        return dispatch(member, rawPath, headOnly);
      } catch (MavenExceptions.MavenNotFoundException ignored) {
        // Nexus group repositories continue to the next member on misses.
      } catch (MavenExceptions.BadUpstreamException ignored) {
        // Nexus' generic group handler only returns successful member responses.
      } catch (MavenExceptions.MethodNotAllowed ignored) {
        // Skip incompatible members; repository creation already validates format/type.
      }
    }
    throw new MavenExceptions.MavenNotFoundException(rawPath);
  }

  private MavenResponse dispatch(RepositoryRuntime member, String rawPath, boolean headOnly) {
    return switch (member.type()) {
      case PROXY -> proxy.get(member, rawPath, headOnly);
      case GROUP -> get(member, rawPath, headOnly);
      case HOSTED -> throw new MavenExceptions.MethodNotAllowed(
          "Go hosted repositories are not supported: " + member.name());
    };
  }
}
