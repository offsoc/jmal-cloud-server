package com.jmal.clouddisk.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;

import java.io.IOException;
import java.util.List;

/**
 * yml 资源加载工厂类
 *
 * @author jmal
 */
public class YamlPropertyLoaderFactory extends DefaultPropertySourceFactory {


    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {

        if (null == resource) {
            super.createPropertySource(name, resource);
        }
        List<PropertySource<?>> sourceList = new YamlPropertySourceLoader().load(resource.getResource().getFilename(), resource.getResource());
        if(sourceList.size() < 1){
            throw new RuntimeException("请配置 file.yml");
        }
        return sourceList.get(0);
    }

}
