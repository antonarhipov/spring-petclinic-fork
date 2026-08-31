package org.springframework.samples.petclinic.shared.command;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.RequestDataValueProcessor;

@Component
final class CommandFormValueProcessorPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if ("requestDataValueProcessor".equals(beanName) && bean instanceof RequestDataValueProcessor processor
				&& !(processor instanceof CommandFormValueProcessor)) {
			return new CommandFormValueProcessor(processor);
		}
		return bean;
	}

}
