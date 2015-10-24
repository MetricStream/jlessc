/**
 * MIT License (MIT)
 *
 * Copyright (c) 2014 - 2015 Volker Berlin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * UT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author Volker Berlin
 * @license: The MIT license <http://opensource.org/licenses/MIT>
 */
package com.inet.lib.less;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Hold all extends that was executed.
 */
class LessExtendMap {

    private final HashMultimap<String, LessExtendResult> all          = new HashMultimap<>();

    private final HashMultimap<String, String[]>         exact        = new HashMultimap<>();

    // use a LinkedHashSet as cache to remove duplicates and hold the original order
    private LinkedHashSet<String>                        selectorList = new LinkedHashSet<>();

    /**
     * Is calling on formatting if an extends was include.
     * @param lessExtend the extend
     * @param mainSelector the selectors in which the extend is placed.
     */
    void add( LessExtend lessExtend, String[] mainSelector ) {
        if( mainSelector == null ) {
            mainSelector = lessExtend.getSelectors();
        } else {
            mainSelector = SelectorUtils.merge( mainSelector, lessExtend.getSelectors() );
        }
        if( mainSelector[0].startsWith( "@media" ) ) {
            //TODO handling of scope
            return;
        }
        String[] extendingSelectors = lessExtend.getExtendingSelectors();
        if( lessExtend.isAll() ) {
            LessExtendResult extend = new LessExtendResult( mainSelector, extendingSelectors );
            for( String selector : extendingSelectors ) {
                SelectorTokenizer tokenizer = new SelectorTokenizer( selector );
                do {
                    String token = tokenizer.next();
                    if( token == null ) {
                        break;
                    }
                    all.add( token, extend );
                } while( true );
            }
        } else {
            for( String selector : extendingSelectors ) {
                exact.add( selector, mainSelector );
            }
        }
    }

    /**
     * Add to the given selectors all possible extends and return the resulting selectors.
     * 
     * @param selectors
     *            current selectors
     * @param isReference
     *            if the current rule is in a less file which was import with "reference" keyword
     * @return the selectors concatenate with extends or the original if there are no etends.
     */
    public String[] concatenateExtends( String[] selectors, boolean isReference ) {
        selectorList.clear();
        for( String selector : selectors ) {
            concatenateExtendsRecursive( selector, isReference );
        }

        if( isReference ) {
            return selectorList.toArray( new String[selectorList.size()] );
        }

        if( selectorList.size() > 0 ) {
            for( String selector : selectors ) {
                selectorList.remove( selector ); // remove duplicates
            }
            if( selectorList.size() > 0 ) {
                int off = selectors.length;
                selectors = Arrays.copyOf( selectors, off + selectorList.size() );
                for( String str : selectorList ) {
                    selectors[off++] = str;
                }
            }
        }
        return selectors;
    }

    /**
     * Add to the given selector all possible extends to the internal selectorList. This method is call recursive.
     * 
     * @param selector
     *            current selector
     * @param isReference
     *            if the current rule is in a less file which was import with "reference" keyword
     */
    private void concatenateExtendsRecursive( String selector, boolean isReference ) {
        List<String[]> list = exact.get( selector );
        if( list != null ) {
            for( String[] lessExtend : list ) {
                for( String sel : lessExtend ) {
                    boolean needRecursion = selectorList.add( sel );
                    if( needRecursion ) { //only if there are new entries then we need to try a recursion, else we have a stack overflow
                        concatenateExtendsRecursive( sel, isReference );
                    }
                }
            }
        }
        SelectorTokenizer tokenizer = new SelectorTokenizer( selector );
        do {
            String token = tokenizer.next();
            if( token == null ) {
                break;
            }
            List<LessExtendResult> results = all.get( token );
            if( results != null ) {
                for( LessExtendResult lessExtend : results ) {
                    for( String extendingSelector : lessExtend.getExtendingSelectors() ) {
                        if( selector.contains( extendingSelector ) ) {
                            for( String replace : lessExtend.getSelectors() ) {
                                String replacedSelector = selector.replace( extendingSelector, replace );
                                boolean needRecursion = selectorList.add( replacedSelector );
                                if( needRecursion && !replacedSelector.contains( extendingSelector ) ) {
                                    concatenateExtendsRecursive( replacedSelector, isReference );
                                }
                            }
                        }
                    }
                }
            }
        } while( true );
    }
}
