package com.hyeokjin.devflow.testagent.test;

import java.util.List;

public record TestCommand(
        String display,
        List<String> arguments
) {
}
