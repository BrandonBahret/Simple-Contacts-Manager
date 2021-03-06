/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

// Source :: https://android.googlesource.com/platform/packages/providers/ContactsProvider/+/eclair-release/src/com/android/providers/contacts/NameSplitter.java

package com.example.brandon.quickcontacts;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;

import java.util.HashSet;
import java.util.StringTokenizer;
/**
 * The purpose of this class is to split a full name into given names and last
 * name. The logic only supports having a single last name. If the full name has
 * multiple last names the output will be incorrect.
 * <p>
 * Core algorithm:
 * <ol>
 * <li>Remove the suffixes (III, Ph.D., M.D.).</li>
 * <li>Remove the prefixes (Mr., Pastor, Reverend, Sir).</li>
 * <li>Assign the last remaining token as the last name.</li>
 * <li>If the previous word to the last name is one from LASTNAME_PREFIXES, use
 * this word also as the last name.</li>
 * <li>Assign the rest of the words as the "given names".</li>
 * </ol>
 */
class NameSplitter {
    private static final int MAX_TOKENS = 10;
    private final HashSet<String> mPrefixesSet;
    private final HashSet<String> mSuffixesSet;
    private final int mMaxSuffixLength;
    private final HashSet<String> mLastNamePrefixesSet;
    private final HashSet<String> mConjuctions;
    static class Name {
        private String prefix;
        private String givenNames;
        private String middleName;
        private String familyName;
        private String suffix;
        Name() {
        }

        String getPrefix() {
            return prefix;
        }
        String getGivenNames() {
            return givenNames;
        }
        String getMiddleName() {
            return middleName;
        }
        String getFamilyName() {
            return familyName;
        }
        String getSuffix() {
            return suffix;
        }
    }
    private static class NameTokenizer extends StringTokenizer {
        private final String[] mTokens;
        private int mDotBitmask;
        private int mStartPointer;
        private int mEndPointer;
        NameTokenizer(String fullName) {
            super(fullName, " .,", true);
            mTokens = new String[MAX_TOKENS];
            // Iterate over tokens, skipping over empty ones and marking tokens that
            // are followed by dots.
            while (hasMoreTokens() && mEndPointer < MAX_TOKENS) {
                final String token = nextToken();
                if (token.length() > 0) {
                    final char c = token.charAt(0);
                    if (c == ' ' || c == ',') {
                        continue;
                    }
                }
                if (mEndPointer > 0 && token.charAt(0) == '.') {
                    mDotBitmask |= (1 << (mEndPointer - 1));
                } else {
                    mTokens[mEndPointer] = token;
                    mEndPointer++;
                }
            }
        }
        /**
         * Returns true if the token is followed by a dot in the original full name.
         */
        boolean hasDot(int index) {
            return (mDotBitmask & (1 << index)) != 0;
        }
    }
    /**
     * Constructor.
     *
     * @param commonPrefixes comma-separated list of common prefixes,
     *            e.g. "Mr, Ms, Mrs"
     * @param commonLastNamePrefixes comma-separated list of common last name prefixes,
     *           e.g. "d', st, st., von"
     * @param commonSuffixes comma-separated list of common suffixes,
     *            e.g. "Jr, M.D., MD, D.D.S."
     * @param commonConjunctions comma-separated list of common conjuctions,
     *            e.g. "AND, Or"
     */
    NameSplitter(String commonPrefixes, String commonLastNamePrefixes,
                        String commonSuffixes, String commonConjunctions) {
        mPrefixesSet = convertToSet(commonPrefixes);
        mLastNamePrefixesSet = convertToSet(commonLastNamePrefixes);
        mSuffixesSet = convertToSet(commonSuffixes);
        mConjuctions = convertToSet(commonConjunctions);
        int maxLength = 0;
        for (String suffix : mSuffixesSet) {
            if (suffix.length() > maxLength) {
                maxLength = suffix.length();
            }
        }
        mMaxSuffixLength = maxLength;
    }
    /**
     * Converts a comma-separated list of Strings to a set of Strings. Trims strings
     * and converts them to upper case.
     */
    private static HashSet<String> convertToSet(String strings) {
        HashSet<String> set = new HashSet<>();
        if (strings != null) {
            String[] split = strings.split(",");
            for(String token:split){
                set.add(token.trim().toUpperCase());
            }
        }
        return set;
    }

    /**
     * Parses a full name and returns parsed components in the Name object.
     */
    void split(Name name, String fullName) {
        if (fullName == null) {
            return;
        }
        NameTokenizer tokens = new NameTokenizer(fullName);
        parsePrefix(name, tokens);
        // If the name consists of just one or two tokens, treat them as first/last name,
        // not as suffix.  Example: John Ma; Ma is last name, not "M.A.".
        if (tokens.mEndPointer > 2) {
            parseSuffix(name, tokens);
        }
        if (name.prefix == null && tokens.mEndPointer - tokens.mStartPointer == 1) {
            name.givenNames = tokens.mTokens[tokens.mStartPointer];
        } else {
            parseLastName(name, tokens);
            parseMiddleName(name, tokens);
            parseGivenNames(name, tokens);
        }
    }
    /**
     * Flattens the given {@link Name} into a single field, usually for storage
     * in {@link StructuredName#DISPLAY_NAME}.
     */

    /**
     * Parses the first word from the name if it is a prefix.
     */
    private void parsePrefix(Name name, NameTokenizer tokens) {
        if (tokens.mStartPointer == tokens.mEndPointer) {
            return;
        }
        String firstToken = tokens.mTokens[tokens.mStartPointer];
        if (mPrefixesSet.contains(firstToken.toUpperCase())) {
            name.prefix = firstToken;
            tokens.mStartPointer++;
        }
    }
    /**
     * Parses the last word(s) from the name if it is a suffix.
     */
    private void parseSuffix(Name name, NameTokenizer tokens) {
        if (tokens.mStartPointer == tokens.mEndPointer) {
            return;
        }
        String lastToken = tokens.mTokens[tokens.mEndPointer - 1];
        if (lastToken.length() > mMaxSuffixLength) {
            return;
        }
        String normalized = lastToken.toUpperCase();
        if (mSuffixesSet.contains(normalized)) {
            name.suffix = lastToken;
            tokens.mEndPointer--;
            return;
        }
        if (tokens.hasDot(tokens.mEndPointer - 1)) {
            lastToken += '.';
        }
        normalized += ".";
        // Take care of suffixes like M.D. and D.D.S.
        int pos = tokens.mEndPointer - 1;
        while (normalized.length() <= mMaxSuffixLength) {
            if (mSuffixesSet.contains(normalized)) {
                name.suffix = lastToken;
                tokens.mEndPointer = pos;
                return;
            }
            if (pos == tokens.mStartPointer) {
                break;
            }
            pos--;
            if (tokens.hasDot(pos)) {
                lastToken = tokens.mTokens[pos] + "." + lastToken;
            } else {
                lastToken = tokens.mTokens[pos] + " " + lastToken;
            }
            normalized = tokens.mTokens[pos].toUpperCase() + "." + normalized;
        }
    }
    private void parseLastName(Name name, NameTokenizer tokens) {
        if (tokens.mStartPointer == tokens.mEndPointer) {
            return;
        }
        name.familyName = tokens.mTokens[tokens.mEndPointer - 1];
        tokens.mEndPointer--;
        // Take care of last names like "D'Onofrio" and "von Cliburn"
        if ((tokens.mEndPointer - tokens.mStartPointer) > 0) {
            String lastNamePrefix = tokens.mTokens[tokens.mEndPointer - 1];
            final String normalized = lastNamePrefix.toUpperCase();
            if (mLastNamePrefixesSet.contains(normalized)
                    || mLastNamePrefixesSet.contains(normalized + ".")) {
                if (tokens.hasDot(tokens.mEndPointer - 1)) {
                    lastNamePrefix += '.';
                }
                name.familyName = lastNamePrefix + " " + name.familyName;
                tokens.mEndPointer--;
            }
        }
    }
    private void parseMiddleName(Name name, NameTokenizer tokens) {
        if (tokens.mStartPointer == tokens.mEndPointer) {
            return;
        }
        if ((tokens.mEndPointer - tokens.mStartPointer) > 1) {
            if ((tokens.mEndPointer - tokens.mStartPointer) == 2
                    || !mConjuctions.contains(tokens.mTokens[tokens.mEndPointer - 2].
                    toUpperCase())) {
                name.middleName = tokens.mTokens[tokens.mEndPointer - 1];
                tokens.mEndPointer--;
            }
        }
    }
    private void parseGivenNames(Name name, NameTokenizer tokens) {
        if (tokens.mStartPointer == tokens.mEndPointer) {
            return;
        }
        if ((tokens.mEndPointer - tokens.mStartPointer) == 1) {
            name.givenNames = tokens.mTokens[tokens.mStartPointer];
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = tokens.mStartPointer; i < tokens.mEndPointer; i++) {
                if (i != tokens.mStartPointer) {
                    sb.append(' ');
                }
                sb.append(tokens.mTokens[i]);
                if (tokens.hasDot(i)) {
                    sb.append('.');
                }
            }
            name.givenNames = sb.toString();
        }
    }
}