/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Adam Fuchs          <afuchs@cs.umd.edu>
 *  Avik Chaudhuri      <avik@cs.umd.edu>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. The names of the contributors may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 *
 */

package org.scandroid.prefixtransfer.modeledAllocations;

import org.scandroid.prefixtransfer.InstanceKeySite;
import org.scandroid.prefixtransfer.PrefixVariable;

public class UriAppendString extends InstanceKeySite {

    final int uriInstanceID;
    final int stringInstanceID;
    final int instanceID;

    public UriAppendString(int instanceID, int uriInstanceID, int stringInstanceID)
    {
        this.uriInstanceID = uriInstanceID;
        this.stringInstanceID = stringInstanceID;
        this.instanceID = instanceID;
    }

    @Override
    public PrefixVariable propagate(PrefixVariable input) {
//      System.out.println("Propagating at: " + instanceID + " (" + constantValue + ")");
        PrefixVariable retVal = new PrefixVariable();
        retVal.copyState(input);
        String prefix = input.getPrefix(uriInstanceID);
        if (input.fullPrefixKnown.contains(uriInstanceID)) {
            retVal.update(instanceID, prefix + "/" + input.getPrefix(stringInstanceID));
            if (input.fullPrefixKnown.contains(stringInstanceID))
                retVal.include(instanceID);
        }
        else retVal.update(instanceID, prefix);
        return retVal;
    }

    public String toString() {
        return ("UriAppendString(instanceID = " + instanceID + "; uriInstanceID = " + uriInstanceID + "; stringInstanceID = " + stringInstanceID + ")");
    }

    @Override
    public int instanceID() {
        return instanceID;
    }

}
