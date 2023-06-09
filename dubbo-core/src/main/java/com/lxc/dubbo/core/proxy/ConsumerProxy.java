package com.lxc.dubbo.core.proxy;


import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.lxc.dubbo.core.excetion.ApiErrCodeException;
import com.lxc.dubbo.domain.Invocation;
import com.lxc.dubbo.domain.Url;
import com.lxc.dubbo.domain.result.RequestResult;
import com.lxc.dubbo.registry.annotaion.FrankDubboReference;
import com.lxc.dubbo.domain.constants.UrlConstant;
import com.lxc.dubbo.registry.cache.LocalConsumerCache;
import com.lxc.dubbo.registry.loadbalance.LoadBalance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import static com.lxc.dubbo.domain.enums.ApiErrCodeExceptionEnum.NO_ALIVE_PROVIDER;

/**
 * 将代理类注入到打了FrankDubboReference注解的对象上
 */
@Component
@Slf4j
public class ConsumerProxy implements BeanPostProcessor {

    @Autowired
    private LoadBalance loadBalance;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        Field[] fields = beanClass.getDeclaredFields();
        Arrays.stream(fields).forEach(field -> {
            if (field.isAnnotationPresent(FrankDubboReference.class)) {
                field.setAccessible(true);
                try {
                    // 核心方法，将代理类注入到打了FrankDubboReference注解的对象上
                    field.set(bean, getProxy(field.getType()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        return bean;
    }

    private Object getProxy(Class interfaceClass) {
        return Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class[]{interfaceClass},
                (proxy, method, args) -> {
                    // 构建一个对象，包含接口名，方法名，方法参数，方法参数类型
                    Invocation invocation = new Invocation(interfaceClass.getName(), method.getName(), args, method.getParameterTypes());
                    // 从本地缓存中获取，当前接口的所有providers
                    List<Url> urls = LocalConsumerCache.get(interfaceClass.getName());
                    if (CollectionUtils.isEmpty(urls)) {
                        log.error("当前接口: {}，没有存活的提供者: {}", interfaceClass.getName(), urls);
                        throw new ApiErrCodeException(NO_ALIVE_PROVIDER);
                    }
                    log.debug("当前接口: {}，存活的提供者: {}", interfaceClass.getName(), urls.toString());
                    // 根据负载均衡策略获取对应的url
                    Url url = loadBalance.getUrl(urls);
                    log.debug("当前接口: {}，选择: {}", interfaceClass.getName(), url);
                    // 使用http请求，远程调用当前接口
                    String result = HttpUtil.post(url.getAddressAndPort() + UrlConstant.RPC_URL, JSON.toJSONString(invocation));
                    RequestResult requestResult = JSON.parseObject(result, RequestResult.class);
                    if (requestResult.isSuccess()) {
                        if (method.getReturnType() == String.class) {
                            return requestResult.getData();
                        }
                        return JSON.toJavaObject((JSON) requestResult.getData(), method.getReturnType());
                    }
                    log.error("提供者返回接口错误: {}", JSON.toJSONString(requestResult));
                    // 如果返回接口错误，说明provider有问题，从本地缓存剔除，防止下次再次调用
                    LocalConsumerCache.remove(interfaceClass.getName(), url);
                    throw new RuntimeException(requestResult.getMessage());
                });
    }
}
