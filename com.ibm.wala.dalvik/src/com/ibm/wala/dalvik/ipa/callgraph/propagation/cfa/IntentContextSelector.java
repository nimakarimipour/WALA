/*
 *  Copyright (c) 2013,
 *      Tobias Blaschke <code@tobiasblaschke.de>
 *  All rights reserved.

 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  3. The names of the contributors may not be used to endorse or promote
 *     products derived from this software without specific prior written
 *     permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 */
package com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa;

import com.ibm.wala.ipa.callgraph.ContextSelector;

import com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.Intent;
import com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.IntentMap;
import com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.IntentContext;
import com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.AndroidContext;
import com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.IntentContextInterpreter;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.Selector;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.propagation.ConstantKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;

import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.ipa.callgraph.propagation.NormalAllocationInNode;
import com.ibm.wala.ipa.callgraph.propagation.AbstractTypeInNode;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;

import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.dalvik.util.AndroidTypes;
import com.ibm.wala.util.intset.EmptyIntSet;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.BimodalMutableIntSet;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.util.collections.HashMapFactory;

import com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.IntentStarters;
import com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.IntentStarters.StartInfo;

import com.ibm.wala.dalvik.util.AndroidEntryPointManager;

import com.ibm.wala.util.strings.StringStuff;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import com.ibm.wala.util.collections.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Adds Intents to the Context of functions that start Android-Components.
 *
 *  This is done by remembering all new-sites where Intent-Objects are built and the parameters to its
 *  Constructor. When a function managed by this Selector (see IntentStarters) is encountered the stored 
 *  information is added to its Context.
 *
 *  @see    com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.IntentContextInterpreter
 *  @see    com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.IntentStarters
 *
 *  @author Tobias Blaschke <code@tobiasblaschke.de>
 *  @since  2013-10-14
 */
public class IntentContextSelector implements ContextSelector {
    private static final Logger logger = LoggerFactory.getLogger(IntentContextSelector.class);

    private final IntentMap intents = new IntentMap();
    private final ContextSelector parent;
    private final IntentStarters intentStarters;
    private final Map<InstanceKey, AndroidContext> seenContext;

    public IntentContextSelector(final IClassHierarchy cha) {
        this(null, cha);
    }

    /**
     *  @param  parent  is always asked to build a Context first. Context generated by this class is added then.
     */
    public IntentContextSelector(final ContextSelector parent, final IClassHierarchy cha) {
        this.parent = parent;
        this.seenContext = HashMapFactory.make();
        this.intentStarters = new IntentStarters(cha);
    }

    /**
     *  Given a calling node and a call site, returns the Context in which the callee should be evaluated.
     *  
     *  {@inheritDoc}
     *
     *  @throws IllegalArgumentException    if the type of a parameter given as actualParameters does not match an expected one
     */
    @Override
    public Context getCalleeTarget(CGNode caller, CallSiteReference site, IMethod callee, InstanceKey[] actualParameters) {
        Context ctx = null;

        if (this.parent != null) {
            ctx = parent.getCalleeTarget(caller, site, callee, actualParameters);
            assert (ctx.get(Intent.INTENT_KEY) == null) : "Already have Intent: " + ctx + " caller " + caller + " callee " + callee;
        }

        if (intentStarters.isStarter(callee.getReference())) {
            // Handle startActivity(), startActivityForResult(), startService() and such

            // Search Android-Context and attach corresponding WALA-Context
            /* {
                final InstanceKey self = actualParameters[0];
                assert (self != null) : "This-Pointer was not marked as relevant!";
                
                if (seenContext.containsKey(self)) {
                    ctx = new AndroidContext(ctx, seenContext.get(self).getContextType());
                } else {
                    logger.warn("No Android-Context seen for {}", caller);
                }
            } // */


            Intent intent = null;
            { // Seach intent 
                for (int j = 0; j < actualParameters.length; ++j) {
                    final InstanceKey param = actualParameters[j];
                    if (param == null) {
                        continue;
                    } else if (param.getConcreteType().getName().equals(AndroidTypes.IntentName)) { 
                        if (! intents.contains(param) ) {
                            logger.error("Unable to resolve Intent called from {}", caller.getMethod());
                            logger.error("Search Key: {} hash: {}", param, param.hashCode());
                            break;
                        } else {
                            intent = intents.find(param);
                            break;
                        }
                    }
                }
            }

            // Add the context
            if (intent != null) {
                AndroidEntryPointManager.MANAGER.addCallSeen(site, intent);
                final Intent iintent = intents.findOrCreateImmutable(intent);
                return new IntentContext(ctx, iintent);
                //return new IntentContext(iintent);
            } else {
                logger.warn("Encountered unresolvable Intent");
                intent = new Intent("Unresolvable");
                intent.setImmutable();
                AndroidEntryPointManager.MANAGER.addCallSeen(site, intent); 
                return new IntentContext(ctx, intent);
                //return new IntentContext(intent);
            }
        } else if (callee.getReference().toString().contains("getSystemService")) {
            assert(actualParameters.length == 2) : "PARAMS LENGTH IS" + actualParameters.length;
            final InstanceKey param = actualParameters[1];

            final Intent intent;
            { // Extract target-Service as intent
                if (param instanceof ConstantKey) {
                    final String target = (String) ((ConstantKey)param).getValue();
                    intent = new Intent(target) {
                        @Override
                        public Intent.IntentType getType() {
                            return Intent.IntentType.SYSTEM_SERVICE;
                        }
                        // TODO override equals and hashCode?
                    };   
                } else {
                    intent = null;
                    if (param == null) {
                        logger.warn("Got param as 'null'. Obviously can't handle this. Caller was: {}", caller.getMethod());
                    } else {
                        logger.warn("Got param as {}. Can't handle this :(", param.getClass());
                    }
                }
            }
            
            // Add the context
            if (intent != null) {
                AndroidEntryPointManager.MANAGER.addCallSeen(site, intent); 
                logger.info("SystemService {} in {} by {}", intent, site, caller);
                final Intent iintent = intents.findOrCreateImmutable(intent);
                return new IntentContext(ctx, iintent);
                //return new IntentContext(iintent);
            }
        } else if (callee.isInit() && callee.getDeclaringClass().getName().equals(AndroidTypes.IntentName)) {
            //
            //  Handle the different Constructors of Intent
            //
            final InstanceKey self = actualParameters[0];
            final Selector calleeSel = callee.getSelector();

            boolean isExplicit = false;
            final InstanceKey uriKey;
            final InstanceKey actionKey;
            { // fetch actionKey, uriKey
                switch (callee.getNumberOfParameters()) { 
                    case 1:
                        logger.debug("Handling Intent()");
                        actionKey = null;
                        uriKey = null;
                        break;
                    case 2:    
                        if (calleeSel.equals(Selector.make("<init>(Ljava/lang/String;)V"))) {
                            logger.debug("Handling Intent(String action)");
                            actionKey = actualParameters[1];  
                        } else if (calleeSel.equals(Selector.make("<init>(Landroid/content/Intent;)V"))) {
                            logger.debug("Handling Intent(Intent other)");

                            final InstanceKey inIntent = actualParameters[1];

                            if (intents.contains(inIntent)) {
                                intents.put(self, intents.find(inIntent));
                            } else {
                                logger.warn("In Intent-Copy constructor: Unable to find the original");
                            }
                            actionKey = null;
                        } else {
                            logger.error("No handling implemented for: {}", callee);
                            actionKey = null;
                        }
                        uriKey = null;
                        break;
                    case 3:
                        if (calleeSel.equals(Selector.make("<init>(Ljava/lang/String;Landroid/net/Uri;)V"))) {
                            logger.debug("Handling Intent(String action, Uri uri)");
                            // TODO: Use Information of the URI...
                            actionKey = actualParameters[1];  
                            uriKey = actualParameters[2];
                        } else if (calleeSel.equals(Selector.make("<init>(Landroid/content/Context;Ljava/lang/Class;)V"))) {
                            logger.debug("Handling Intent(Context, Class)");
                            actionKey = actualParameters[2];
                            uriKey = null;
                            isExplicit = true;
                        } else {
                            logger.error("No handling implemented for: {}",  callee);
                            actionKey = null;
                            uriKey = null;
                        }
                        break;
                    case 5:
                        if (calleeSel.equals(Selector.make("<init>(Ljava/lang/String;Landroid/net/Uri;Landroid/content/Context;Ljava/lang/Class;)V"))) {
                            logger.debug("Handling Intent(String action, Uri uri, Context, Class)");
                            actionKey = actualParameters[4];
                            uriKey = actualParameters[2];
                            isExplicit = true;
                        } else {
                            logger.error("No handling implemented for: {}", callee);
                            actionKey = null;
                            uriKey = null;
                        }
                        break;
                    default:
                        logger.error("Can't extract Info from Intent-Constructor: {} (not implemented)", site);
                        actionKey = null;
                        uriKey = null;
                }
            } // of fetch actionKey

            final Intent intent = intents.findOrCreate(self);   // Creates Wala-internal Intent

            if (actionKey == null) {
                logger.trace("Got action as 'null'. Obviously can't handle this. Caller was {}", caller.getMethod());
                if (isExplicit) {
                    logger.warn("An Intent with undeteminable target would be explicit - unbinding. Caller was {}", caller.getMethod());
                    intent.unbind();
                }
            } else {
                intents.setAction(self, actionKey, isExplicit);
            }
            //final Intent intent = intents.find(self);
            //if (isExplicit && (! intent.isExplicit())) {    // Has to check if already explicit as we get here multiple times
            //    intents.setExplicit(self);
            //}

            logger.debug("Setting the target of Intent {} in {} by {}", intent, site, caller);
            // TODO: Evaluate uriKey
        } else if (callee.getSelector().equals(Selector.make("setAction(Ljava/lang/String;)Landroid/content/Intent;")) && 
                callee.getDeclaringClass().getName().equals(AndroidTypes.IntentName)) {
            final InstanceKey self = actualParameters[0];
            final InstanceKey actionKey = actualParameters[1];
            final Intent intent = intents.find(self);

            if (AndroidEntryPointManager.MANAGER.isAllowIntentRerouting()) {
                logger.warn("Re-Setting the target of Intent {} in {} by {}", intent, site, caller);
                intents.setAction(self, actionKey, false); // May unbind internally
            } else {
                intents.unbind(self);
            }
            logger.info("Encountered Intent.setAction - Intent is now: {}", intent);
        } else if (callee.getSelector().equals(Selector.make("setComponent(Landroid/content/ComponentName;)Landroid/content/Intent;"))) {
            // TODO: We can't extract from ComponentName yet.
            final InstanceKey self = actualParameters[0];
            final Intent intent = intents.find(self);

            logger.warn("Re-Setting the target of Intent {} in {} by {}", intent, site, caller);
            
            intent.setExplicit();
            intents.unbind(self);
        } else if (callee.getSelector().equals(Selector.make("setClass(Landroid/content/Context;Ljava/lang/Class;)Landroid/content/Intent;")) || 
                callee.getSelector().equals(Selector.make("setClassName(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;")) ||
                callee.getSelector().equals(Selector.make("setClassName(Landroid/content/Context;Ljava/lang/String;)Landroid/content/Intent;"))) {
            final InstanceKey self = actualParameters[0];
            final InstanceKey actionKey = actualParameters[2];
            final Intent intent = intents.find(self);

            if (AndroidEntryPointManager.MANAGER.isAllowIntentRerouting()) {
                logger.warn("Re-Setting the target of Intent {} in {} by {}", intent, site, caller);
                intents.setAction(self, actionKey, true);
            } else {
                intents.unbind(self);
            }
            logger.info("Encountered Intent.setClass - Intent is now: {}", intent);
        } else if (callee.getSelector().equals(Selector.make("fillIn(Landroid/content/Intent;I)I"))) {
            // See 'setAction' before...                                                        TODO
            logger.warn("Intent.fillIn not implemented - Caller: {}", caller);
            final InstanceKey self = actualParameters[0];
            intents.unbind(self);
        } else if (callee.isInit() && callee.getDeclaringClass().getName().equals(AndroidTypes.IntentSenderName)) {
            logger.error("Unable to evaluate IntentSender: Not implemented!");   // TODO
        } /*else if (site.isSpecial() && callee.getDeclaringClass().getName().equals(
                    AndroidTypes.ContextWrapperName)) {
            final InstanceKey baseKey = actualParameters[1];  
            final InstanceKey wrapperKey = actualParameters[0];  

            logger.debug("Handling ContextWrapper(Context base)");
            if (seenContext.containsKey(baseKey)) {
                seenContext.put(wrapperKey, seenContext.get(baseKey));
            } else {
                if (baseKey == null) {
                    logger.trace("Got baseKey as 'null'. Obviously can't handle this. Caller was: {}", caller.getMethod());
                } else {
                    logger.warn("ContextWrapper: No AndroidContext was seen for baseKey");
                }
            }
        } else if ((site.isSpecial() && callee.getDeclaringClass().getName().equals(
                        AndroidTypes.ContextImplName))) {
            final InstanceKey self = actualParameters[0];
            seenContext.put(self, new AndroidContext(ctx, AndroidTypes.AndroidContextType.CONTEXT_IMPL)); 
        } else if (callee.getDeclaringClass().getName().equals(AndroidTypes.ContextWrapperName) &&
                callee.getSelector().equals(Selector.make("attachBaseContext(Landroid/content/Context;)V"))) {
            final InstanceKey baseKey = actualParameters[1];  
            final InstanceKey wrapperKey = actualParameters[0];  
       
            logger.debug("Handling ContextWrapper.attachBaseContext(base)");
             if (seenContext.containsKey(baseKey)) {
                seenContext.put(wrapperKey, seenContext.get(baseKey));
            } else {
                if (baseKey == null) {
                    logger.trace("Got baseKey as 'null'. Obviously can't handle this. Caller was: {}", caller.getMethod());
                } else {
                    logger.warn("ContextWrapper: No AndroidContext was seen for baseKey");
                }
            }
        } */

        return ctx;
    }

    /**
     *  Given a calling node and a call site, return the set of parameters based on which this selector may choose 
     *  to specialize contexts. 
     *
     *  {@inheritDoc}
     */
    @Override
    public IntSet getRelevantParameters(CGNode caller, CallSiteReference site) {
        IntSet ret;
        if (this.parent != null) {
            ret = this.parent.getRelevantParameters(caller, site);
        } else {
            ret = EmptyIntSet.instance;
        }

        final MethodReference target = site.getDeclaredTarget();
        if (intentStarters.isStarter(target)) {
            final StartInfo info = intentStarters.getInfo(target);
            final int[] relevant = info.getRelevant();

            if (relevant != null) {
                for (int i = 0; i < relevant.length; ++i) {
                    ret = IntSetUtil.add(ret, relevant[i]);
                }
            }
            
            logger.debug("Get relevant for {} is {}", site, ret);
        } else if (site.isSpecial() && target.getDeclaringClass().getName().equals(
                    AndroidTypes.IntentName)) {                

            final MethodReference mRef = site.getDeclaredTarget();
            final int numArgs = mRef.getNumberOfParameters();

            // Intent()
            // Intent(Intent o)
            // Intent(String action)
            // Intent(String action, Uri uri)
            // Intent(Context packageContext, Class<?> cls)
            // Intent(String action, Uri uri, Context packageContext, Class<?> cls)

            // Select all params;
            switch (numArgs) {
                case 0:
                    return EmptyIntSet.instance;
                case 1:
                    return IntSetUtil.make(new int[] { 0, 1 });
                case 2:
                    logger.debug("Got Intent Constructor of: {}", site.getDeclaredTarget().getSelector());
                    return IntSetUtil.make(new int[] { 0, 1, 2 });
                case 3:
                    logger.debug("Got Intent Constructor of: {}", site.getDeclaredTarget().getSelector());
                    return IntSetUtil.make(new int[] { 0, 1, 2, 3 });
                case 4:
                    logger.debug("Got Intent Constructor of: {}", site.getDeclaredTarget().getSelector());
                    return IntSetUtil.make(new int[] { 0, 1, 2, 3, 4 });
                default:
                    logger.debug("Got Intent Constructor of: {}", site.getDeclaredTarget().getSelector());
                    return IntSetUtil.make(new int[] { 0, 1, 2, 3, 4, 5 });
            }
        } else if (site.isSpecial() && target.getDeclaringClass().getName().equals(
                    AndroidTypes.IntentSenderName)) {
            // public IntentSender(IIntentSender target)
            // public IntentSender(IBinder target)
            logger.warn("Encountered an IntentSender-Object");
            return IntSetUtil.make(new int[] { 0, 1 });
        } /*else if (site.isSpecial() && target.getDeclaringClass().getName().equals(
                    AndroidTypes.ContextWrapperName)) {
            logger.debug("Fetched ContextWrapper ctor");
            return IntSetUtil.make(new int[] { 0, 1 });
        } else if ((site.isSpecial() && target.getDeclaringClass().getName().equals(
                        AndroidTypes.ContextImplName))) {
            logger.debug("Fetched Context ctor");
            return IntSetUtil.make(new int[] { 0 });
        } else if (target.getDeclaringClass().getName().equals(AndroidTypes.ContextWrapperName) &&
                target.getSelector().equals(Selector.make("attachBaseContext(Landroid/content/Context;)V"))) {
            logger.debug("Encountered ContextWrapper.attachBaseContext()");
            return IntSetUtil.make(new int[] { 0, 1 });
        }*/ else if (target.getSelector().equals(Selector.make("getSystemService(Ljava/lang/String;)Ljava/lang/Object;"))) {
            logger.debug("Encountered Context.getSystemService()");
            return IntSetUtil.make(new int[] { 0, 1 });
        } else if (target.getSelector().equals(Selector.make("setAction(Ljava/lang/String;)Landroid/content/Intent;"))) {
            return IntSetUtil.make(new int[] { 0, 1 });
        } else if (target.getSelector().equals(Selector.make("setComponent(Landroid/content/ComponentName;)Landroid/content/Intent;"))) {
            return IntSetUtil.make(new int[] { 0 });
        } else if (target.getSelector().equals(Selector.make("setClass(Landroid/content/Context;Ljava/lang/Class;)Landroid/content/Intent;"))) {
            return IntSetUtil.make(new int[] { 0, 2 });
        } else if (target.getSelector().equals(Selector.make("setClassName(Landroid/content/Context;Ljava/lang/String;)Landroid/content/Intent;"))) {
            return IntSetUtil.make(new int[] { 0, 2 });
        } else if (target.getSelector().equals(Selector.make("setClassName(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;"))) {
            return IntSetUtil.make(new int[] { 0, 2 });
        }



        return ret;
    } //else if (site.isSpecial() && target.getDeclaringClass().getName().equals(

}
