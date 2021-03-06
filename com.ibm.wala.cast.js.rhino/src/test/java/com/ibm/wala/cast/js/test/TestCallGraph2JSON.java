package com.ibm.wala.cast.js.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertArrayEquals;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ibm.wala.cast.js.html.DefaultSourceExtractor;
import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.cast.js.util.CallGraph2JSON;
import com.ibm.wala.cast.js.util.FieldBasedCGUtil;
import com.ibm.wala.cast.js.util.FieldBasedCGUtil.BuilderType;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import org.hamcrest.core.IsCollectionContaining;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCallGraph2JSON {

  private FieldBasedCGUtil util;

  @Before
  public void setUp() throws Exception {
    util = new FieldBasedCGUtil(new CAstRhinoTranslatorFactory());
  }

  @Test
  public void testBasic() throws WalaException, CancelException {
    String script = "tests/fieldbased/simple.js";
    CallGraph cg = buildCallGraph(script);
    CallGraph2JSON cg2JSON = new CallGraph2JSON(true);
    Map<String, String[]> parsed = getParsedJSONCG(cg, cg2JSON);
    Assert.assertEquals(5, parsed.keySet().size());
    parsed.values().stream()
        .forEach(
            callees -> {
              Assert.assertEquals(1, callees.length);
            });
  }

  @Test
  public void testNative() throws WalaException, CancelException {
    String script = "tests/fieldbased/native_call.js";
    CallGraph cg = buildCallGraph(script);
    CallGraph2JSON cg2JSON = new CallGraph2JSON(false);
    Map<String, String[]> parsed = getParsedJSONCG(cg, cg2JSON);
    assertArrayEquals(
        new String[] {"Array_prototype_pop (Native)"},
        getTargetsStartingWith(parsed, "native_call.js@2"));
  }

  @Test
  public void testReflectiveCalls() throws WalaException, CancelException {
    String script = "tests/fieldbased/reflective_calls.js";
    CallGraph cg = buildCallGraph(script);
    CallGraph2JSON cg2JSON = new CallGraph2JSON(false);
    Map<String, String[]> parsed = getParsedJSONCG(cg, cg2JSON);
    assertArrayEquals(
        new String[] {"Function_prototype_call (Native)"},
        getTargetsStartingWith(parsed, "reflective_calls.js@10"));
    assertArrayEquals(
        new String[] {"Function_prototype_apply (Native)"},
        getTargetsStartingWith(parsed, "reflective_calls.js@11"));
    assertThat(
        Arrays.asList(getTargetsStartingWith(parsed, "Function_prototype_call (Native)")),
        hasItemStartingWith("reflective_calls.js@1"));
    assertThat(
        Arrays.asList(getTargetsStartingWith(parsed, "Function_prototype_apply (Native)")),
        hasItemStartingWith("reflective_calls.js@5"));
  }

  @Test
  public void testNativeCallback() throws WalaException, CancelException {
    String script = "tests/fieldbased/native_callback.js";
    CallGraph cg = buildCallGraph(script);
    CallGraph2JSON cg2JSON = new CallGraph2JSON(false);
    Map<String, String[]> parsed = getParsedJSONCG(cg, cg2JSON);
    assertArrayEquals(
        new String[] {"Array_prototype_map (Native)"},
        getTargetsStartingWith(parsed, "native_callback.js@2"));
    assertThat(
        Arrays.asList(getTargetsStartingWith(parsed, "Function_prototype_call (Native)")),
        hasItemStartingWith("native_callback.js@3"));
  }

  private static Map<String, String[]> getParsedJSONCG(CallGraph cg, CallGraph2JSON cg2JSON) {
    String json = cg2JSON.serialize(cg);
    // System.err.println(json);
    Gson gson = new Gson();
    Type mapType = new TypeToken<Map<String, String[]>>() {}.getType();
    return gson.fromJson(json, mapType);
  }

  private CallGraph buildCallGraph(String script) throws WalaException, CancelException {
    URL scriptURL = TestCallGraph2JSON.class.getClassLoader().getResource(script);
    return util.buildCG(
            scriptURL, BuilderType.OPTIMISTIC_WORKLIST, null, false, DefaultSourceExtractor::new)
        .fst;
  }

  /**
   * We need this method since column offsets can differ across platforms, so we can't do an exact
   * position match
   */
  private static String[] getTargetsStartingWith(Map<String, String[]> parsedJSON, String prefix) {
    for (String key : parsedJSON.keySet()) {
      if (key.startsWith(prefix)) {
        return parsedJSON.get(key);
      }
    }
    throw new RuntimeException(prefix + " not a key prefix");
  }

  /**
   * We need this method since column offsets can differ across platforms, so we can't do an exact
   * position match
   */
  private static IsCollectionContaining<String> hasItemStartingWith(String prefix) {
    return new IsCollectionContaining<>(startsWith(prefix));
  }
}
