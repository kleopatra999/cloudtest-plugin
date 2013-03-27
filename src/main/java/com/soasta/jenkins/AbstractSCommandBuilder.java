/*
 * Copyright (c) 2013, SOASTA, Inc.
 * All Rights Reserved.
 */
package com.soasta.jenkins;

import java.io.IOException;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;

import hudson.AbortException;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;

public abstract class AbstractSCommandBuilder extends Builder {
    /**
     * URL of {@link CloudTestServer}.
     */
    private final String url;
    
    private FilePath scommand;
  
    public AbstractSCommandBuilder(String url) {
        this.url = url;
    }
  
    public CloudTestServer getServer() {
        return CloudTestServer.get(url);
    }
  
    public String getUrl() {
        return url;
    }
  
    protected ArgumentListBuilder getSCommandArgs(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException {
      CloudTestServer s = getServer();
      if (s == null)
          throw new AbortException("No TouchTest server is configured in the system configuration.");
  
      // Download SCommand, if needed.

      // We remember the location for next time, since this might be called
      // more than once for a single build step (e.g. TestCompositionRunner
      // with a list of compositions).
      
      // As far as I know, this null check does not need to be thread-safe.
      if (scommand == null)
          scommand = new SCommandInstaller(s).scommand(build.getBuiltOn(), listener);
  
      ArgumentListBuilder args = new ArgumentListBuilder();
      args.add(scommand)
          .add("url=" + s.getUrl())
          .add("username="+s.getUsername())
          .addMasked("password=" + s.getPassword());
      
      ProxyConfiguration proxyConfig = Jenkins.getInstance().proxy;

      if (proxyConfig != null && proxyConfig.name != null) {
          // Jenkins is configured to use a proxy server.

          // Extract the destination CloudTest host.
          String host = s.getUrl().getHost();

          // Check if the proxy applies for this destination host.
          // This code is more or less copied from ProxyConfiguration.createProxy() :-(.
          boolean isNonProxyHost = false;
          for (Pattern p : proxyConfig.getNoProxyHostPatterns()) {
              if (p.matcher(host).matches()) {
                  // It's a match.
                  // Don't use the proxy.
                  isNonProxyHost = true;

                  // No need to continue checking the list.
                  break;
              }
          }

          if (!isNonProxyHost) {
              // Add the SCommand proxy parameters.
              args.add("httpproxyhost=" + proxyConfig.name)
                  .add("httpproxyport=" + proxyConfig.port);

              // If there are proxy credentials, add those too.
              if (proxyConfig.getUserName() != null) {
                  args.add("httpproxyusername=" + proxyConfig.getUserName())
                      .addMasked("httpproxypassword=" + proxyConfig.getPassword());
              }
          }
      }

      return args;
    }
}
