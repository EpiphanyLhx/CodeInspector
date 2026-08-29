package com.codeinspector.service;

import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;

/**
 * 强制走指定 HTTP 代理的 JGit 连接工厂。
 * <p>包装 JGit 默认的 {@link JDKHttpConnectionFactory}，无论上层是否传入代理，
 * 都使用构造时给定的代理创建连接。仅作用于 JGit，不改变 JVM 全局 ProxySelector，
 * 因此不会影响 AI 审查等其它 HTTP 出站请求。</p>
 */
public class ProxiedHttpConnectionFactory implements HttpConnectionFactory {

    private final JDKHttpConnectionFactory delegate = new JDKHttpConnectionFactory();
    private final Proxy proxy;

    public ProxiedHttpConnectionFactory(Proxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public HttpConnection create(URL url) throws IOException {
        return delegate.create(url, proxy);
    }

    @Override
    public HttpConnection create(URL url, Proxy ignored) throws IOException {
        // 忽略 JGit 自行选择的代理，强制使用配置的代理
        return delegate.create(url, proxy);
    }
}
