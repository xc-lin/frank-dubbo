package com.lxc.dubbo.core.invocation;

import com.alibaba.fastjson.JSON;
import com.lxc.dubbo.domain.Invocation;
import com.lxc.dubbo.domain.ObjectInfo;
import com.lxc.dubbo.domain.enums.ApiErrCodeExceptionEnum;
import com.lxc.dubbo.core.excetion.ApiErrCodeException;
import com.lxc.dubbo.domain.constants.UrlConstant;
import com.lxc.dubbo.core.util.ApplicationContextUtil;
import com.lxc.dubbo.registry.cache.LocalProviderCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * provider的请求分发
 */
@RestController
@Slf4j
public class ProviderMethodInvocation {

    @PostMapping(UrlConstant.RPC_URL)
    public Object dubbo(@RequestBody Invocation invocation) throws Exception {
        // 根据invocation 从本地缓存中获取当前接口的实现类
        ObjectInfo objectInfo = LocalProviderCache.get(invocation.getInterfaceName());
        if (Objects.isNull(objectInfo)) {
            log.error("interface未暴露到frank mini dubbo rpc调用中，interfaceName: {}", invocation.getInterfaceName());
            throw new ApiErrCodeException(ApiErrCodeExceptionEnum.INTERFACE_NOT_EXPORT);
        }
        log.info("执行{}.{}", invocation.getInterfaceName(), invocation.getMethodName());
        // 获取当前接口实现类的class实现
        Class clazz = objectInfo.getClazz();
        Method method = null;
        try {
            // 获取当前接口实现类的方法
            method = clazz.getMethod(invocation.getMethodName(), invocation.getParamTypes());
        } catch (NoSuchMethodException exception) {
            log.error("{}.{}方法未暴露到frank mini dubbo rpc调用中", invocation.getInterfaceName(), invocation.getMethodName());
            throw new ApiErrCodeException(ApiErrCodeExceptionEnum.INTERFACE_NOT_EXPORT);
        }
        if (Objects.isNull(method)) {
            log.error("{}.{}方法未暴露到frank mini dubbo rpc调用中", invocation.getInterfaceName(), invocation.getMethodName());
            throw new ApiErrCodeException(ApiErrCodeExceptionEnum.INTERFACE_NOT_EXPORT);
        }
        // 从spring容器中，获取当前实现类的bean
        Object bean = ApplicationContextUtil.getSpringBeanByTypeAndId(objectInfo.getBeanName(), clazz);
        if (Objects.isNull(bean)) {
            log.error("接口: {}, beanName: {}, 未能在spring容器中找到", invocation.getInterfaceName(), objectInfo.getBeanName());
            throw new ApiErrCodeException(ApiErrCodeExceptionEnum.OBJECT_NOT_IN_SPRING);
        }
        // 调用方法
        Object result = method.invoke(bean, invocation.getParams());
        log.info("result:{}", JSON.toJSONString(result));
        return result;
    }
}

