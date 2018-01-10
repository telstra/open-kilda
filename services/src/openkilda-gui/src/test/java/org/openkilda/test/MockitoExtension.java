package org.openkilda.test;

import java.lang.reflect.Parameter;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class MockitoExtension implements TestInstancePostProcessor, ParameterResolver {

	@Override
	public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
		MockitoAnnotations.initMocks(testInstance);

	}

	@Override
	public Object resolve(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
		return getMock(parameterContext.getParameter(), extensionContext);

	}

	@Override
	public boolean supports(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
		return parameterContext.getParameter().isAnnotationPresent(Mock.class);
	}

	private Object getMock(Parameter parameter, ExtensionContext extensionContext) {
		Class<?> mockType = parameter.getType();
		Store mocks = extensionContext.getStore(Namespace.create(MockitoExtension.class, mockType));
		String mockName = getMockName(parameter);

		if (mockName != null) {
			return mocks.getOrComputeIfAbsent(mockName, key -> mock(mockType, mockName));
		}
		return mocks.getOrComputeIfAbsent(mockType.getCanonicalName(), key -> mock(mockType));
	}

	private String getMockName(Parameter parameter) {
		String explicitMockName = parameter.getAnnotation(Mock.class).name().trim();
		if (!explicitMockName.isEmpty()) {
			return explicitMockName;
		} else if (parameter.isNamePresent()) {
			return parameter.getName();
		}
		return null;
	}

}
