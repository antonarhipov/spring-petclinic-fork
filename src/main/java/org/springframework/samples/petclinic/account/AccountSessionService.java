package org.springframework.samples.petclinic.account;

import java.util.Map;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AccountSessionService {

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	public AccountSessionService(FindByIndexNameSessionRepository<? extends Session> sessions) {
		this.sessions = sessions;
	}

	public void rotateSessionId(HttpServletRequest request) {
		if (request != null && request.getSession(false) != null) {
			request.changeSessionId();
		}
	}

	public void invalidateAllSessions(String username) {
		Map<String, ? extends Session> found = this.sessions.findByPrincipalName(username);
		found.keySet().forEach(this.sessions::deleteById);
	}

}
