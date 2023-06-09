package com.lxc.dubbo.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Invocation {

    private String interfaceName;

    private String methodName;

    private Object[] params;

    private Class[] paramTypes;
}
