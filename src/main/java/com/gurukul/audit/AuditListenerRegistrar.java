package com.gurukul.audit;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.stereotype.Component;

/** Hooks AuditEventListener into Hibernate's post-insert/update/delete events at startup. */
@Component
@RequiredArgsConstructor
public class AuditListenerRegistrar {

	private final EntityManagerFactory entityManagerFactory;
	private final AuditEventListener auditEventListener;

	@PostConstruct
	void register() {
		EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
				.getServiceRegistry()
				.requireService(EventListenerRegistry.class);
		registry.appendListeners(EventType.POST_INSERT, auditEventListener);
		registry.appendListeners(EventType.POST_UPDATE, auditEventListener);
		registry.appendListeners(EventType.POST_DELETE, auditEventListener);
	}

}
