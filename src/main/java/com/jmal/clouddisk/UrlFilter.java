package com.jmal.clouddisk;


import com.jmal.clouddisk.config.FileProperties;
import io.milton.config.HttpManagerBuilder;
import io.milton.http.HttpManager;
import io.milton.http.Request;
import io.milton.http.ResourceFactory;
import io.milton.http.Response;
import io.milton.http.annotated.AnnotationResourceFactory;
import io.milton.http.template.JspViewResolver;
import io.milton.http.template.ViewResolver;
import io.milton.servlet.MiltonServlet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author jmal
 * @Description 路径过滤器
 * @Date 2020/10/20 4:12 下午
 */
@Component
public class UrlFilter implements Filter {

    private static final String API = "/api";
    private static final Pattern COMPILE = Pattern.compile(API);

    @Autowired
    private FileProperties fileProperties;

    @Autowired
    private HttpManagerBuilder httpManagerBuilder;

    private HttpManager httpManager;

    @Override
    public void init(FilterConfig filterConfig) {
        ResourceFactory rf = httpManagerBuilder.getMainResourceFactory();
        if (rf instanceof AnnotationResourceFactory) {
            AnnotationResourceFactory arf = (AnnotationResourceFactory) rf;
            if (arf.getViewResolver() == null) {
                ViewResolver viewResolver = new JspViewResolver(filterConfig.getServletContext());
                arf.setViewResolver(viewResolver);
            }
        }
        this.httpManager = httpManagerBuilder.buildHttpManager();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest)request;
        String uri = httpRequest.getRequestURI();
        if(uri.startsWith(API)) {
            uri = COMPILE.matcher(uri).replaceFirst("");
            httpRequest.getRequestDispatcher(uri).forward(request,response);
        }
        // 以/webDAV 开头的走webDAV协议
        if(uri.startsWith(fileProperties.getWebDavPrefix())){
            doMiltonProcessing((HttpServletRequest) request, (HttpServletResponse) response);
        }
        chain.doFilter(request,response);
    }

    private void doMiltonProcessing(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            MiltonServlet.setThreadlocals(req, resp);
            Request request = new io.milton.servlet.ServletRequest(req, req.getServletContext());
            Response response = new io.milton.servlet.ServletResponse(resp);
            if("OPTIONS".equals(req.getMethod()) && StringUtils.isEmpty(req.getHeader("authorization"))){
                List<String> list = new ArrayList<>();
                list.add("Basic realm =\" server\"");
                response.setAuthenticateHeader(list);
            }
            httpManager.process(request, response);
        } finally {
            MiltonServlet.clearThreadlocals();
            resp.flushBuffer();
        }
    }

    @Override
    public void destroy() {
        if (httpManager != null) {
            httpManager.shutdown();
        }
    }

}
