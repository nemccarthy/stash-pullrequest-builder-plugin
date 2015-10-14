package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import org.junit.Test;
import stashpullrequestbuilder.stashpullrequestbuilder.StashRepository;

import java.util.AbstractMap;
import java.util.Map;

/**
 * Created by nathan on 7/06/2015.
 */
public class AdditionalParameterRegExTest {

    @Test
    public void testSingleParameter() throws Exception {
    	AbstractMap.SimpleEntry<String,String> test1 = StashRepository.getParameter("p:customer=Nickesocke");
    	assert (test1 != null);
    	assert ("customer".equals(test1.getKey()));
    	assert ("Nickesocke".equals(test1.getValue()));
    }

    @Test
    public void testSpacedParameter() throws Exception {
    	AbstractMap.SimpleEntry<String,String> test1 = StashRepository.getParameter("p:customer=Nicke socke#");
    	assert (test1 != null);
    	assert ("customer".equals(test1.getKey()));
    	assert ("Nicke socke#".equals(test1.getValue()));
    }

    @Test
    public void testBlankParameter() throws Exception {
    	AbstractMap.SimpleEntry<String,String> test1 = StashRepository.getParameter("p:the_blank_parameteR=");
    	assert (test1 != null);
    	assert ("the_blank_parameteR".equals(test1.getKey()));
    	assert ("".equals(test1.getValue()));
    }

    @Test
    public void testInvalidParameter() throws Exception {
    	AbstractMap.SimpleEntry<String,String> test1 = StashRepository.getParameter("p:if apa=Nickesocke");
    	assert (test1 == null);
    	AbstractMap.SimpleEntry<String,String> test2 = StashRepository.getParameter("p:apa==Nickesocke");
    	assert (test2 != null &&
				test2.getKey().equals("apa") &&
				test2.getValue().equals("=Nickesocke")
		);
    	AbstractMap.SimpleEntry<String,String> test3 = StashRepository.getParameter("p:I want to make sure that a use of = will not trigger parameter");
    	assert (test3 == null);
    	AbstractMap.SimpleEntry<String,String> test4 = StashRepository.getParameter("p:=nothing");
    	assert (test4 == null);
    	AbstractMap.SimpleEntry<String,String> test5 = StashRepository.getParameter("=nothing");
    	assert (test5 == null);
   }

    @Test
    public void testMultipleParameters() throws Exception {
    	Map<String, String> emptyParameters = StashRepository.getParametersFromContent("");
    	assert (emptyParameters.isEmpty());

    	Map<String, String> singleParameters = StashRepository.getParametersFromContent("p:param1=nothing");
    	assert (singleParameters.size() == 1);
    	assert (singleParameters.get("param1").equals("nothing"));

    	Map<String, String> multipleParameters = StashRepository.getParametersFromContent("p:param1=nothing\rp:param2=something special\np:param3=\r\r\n\rjumping to conclusions\r\r\n\n");
    	assert (multipleParameters.size() == 3);
    	assert (multipleParameters.get("param1").equals("nothing"));
    	assert (multipleParameters.get("param2").equals("something special"));
    	assert (multipleParameters.get("param3").equals(""));
    }
}