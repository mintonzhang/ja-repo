package com.github.klboke.nexusplus.server.raw;

import com.github.klboke.nexusplus.server.maven.MavenExceptions;
import com.github.klboke.nexusplus.server.maven.MavenResponse;
import com.github.klboke.nexusplus.server.maven.RepositoryRuntime;
import org.springframework.stereotype.Service;

@Service
public class RawGroupService {
  private final RawHostedService hosted;
  private final RawProxyService proxy;

  public RawGroupService(RawHostedService hosted, RawProxyService proxy) {
    this.hosted = hosted;
    this.proxy = proxy;
  }

  public MavenResponse get(RepositoryRuntime group, String rawPath, boolean headOnly) {
    if (!group.isGroup()) {
      throw new IllegalStateException("RawGroupService.get called on non-group " + group.name());
    }
    if (group.members().isEmpty()) {
      throw new MavenExceptions.MavenNotFoundException(rawPath);
    }
    for (RepositoryRuntime member : group.members()) {
      try {
        return dispatch(member, rawPath, headOnly);
      } catch (MavenExceptions.MavenNotFoundException ignored) {
        // try next member
      } catch (MavenExceptions.BadUpstreamException e) {
        // Nexus group repositories only return successful member responses; keep probing.
      } catch (MavenExceptions.MethodNotAllowed ignored) {
        // member cannot serve this read; skip
      }
    }
    throw new MavenExceptions.MavenNotFoundException(rawPath);
  }

  private MavenResponse dispatch(RepositoryRuntime member, String rawPath, boolean headOnly) {
    return switch (member.type()) {
      case HOSTED -> hosted.get(member, rawPath, headOnly);
      case PROXY -> proxy.get(member, rawPath, headOnly);
      case GROUP -> get(member, rawPath, headOnly);
    };
  }
}
