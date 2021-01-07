package org.springframework.web.util;

import com.newrelic.agent.bridge.AgentBridge;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Transaction;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;
import com.nr.agent.instrumentation.SpringControllerUtility;

import javax.servlet.http.HttpServletRequest;
import java.util.logging.Level;

@Weave
public class UrlPathHelper {

    public UrlPathHelper(){}

    public String getLookupPathForRequest(HttpServletRequest request) {
        String result = Weaver.callOriginal();
        Transaction transaction = AgentBridge.getAgent().getTransaction(false);

        if(result != null && transaction != null){
            NewRelic.getAgent().getLogger().log(Level.INFO,"BRAD " + result);

            SpringControllerUtility.resourcePath.set(result);
        }

        return result;
    }
}