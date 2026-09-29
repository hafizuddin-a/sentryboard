/*
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
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.protection;

import java.util.ArrayList;
import java.util.List;

/**
 * The screens the guard covers, as (package, part of class name) pairs. Pure Java.
 *
 * The class name is matched as a substring because manufacturers rename or wrap the system
 * Settings activities; the list lives in {@code res/xml/guard_targets.xml} so it can be adjusted
 * per phone without touching code.
 */
public final class GuardTargets {
    private static final class Target {
        final String mPackage;
        final String mClassContains;

        Target(final String packageName, final String classContains) {
            mPackage = packageName;
            mClassContains = classContains;
        }
    }

    private final List<Target> mTargets = new ArrayList<>();

    public GuardTargets add(final String packageName, final String classContains) {
        if (packageName != null && !packageName.isEmpty()
                && classContains != null && !classContains.isEmpty()) {
            mTargets.add(new Target(packageName, classContains));
        }
        return this;
    }

    public int size() {
        return mTargets.size();
    }

    /**
     * @return true if the package is exactly a target's package and the class name contains that
     * target's class fragment. Case-sensitive, so near-misses do not match.
     */
    public boolean matches(final String packageName, final String className) {
        if (packageName == null || className == null) {
            return false;
        }
        for (final Target target : mTargets) {
            if (target.mPackage.equals(packageName) && className.contains(target.mClassContains)) {
                return true;
            }
        }
        return false;
    }
}
