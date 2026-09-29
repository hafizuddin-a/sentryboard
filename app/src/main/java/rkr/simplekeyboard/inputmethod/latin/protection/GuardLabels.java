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

import rkr.simplekeyboard.inputmethod.R;

/** Display names of the guard's requirements. */
public final class GuardLabels {
    private GuardLabels() {
        // Intentional empty constructor for utility class.
    }

    public static int title(final GuardCondition.Requirement requirement) {
        switch (requirement) {
            case DEVICE_ADMIN: return R.string.guard_req_device_admin;
            case USAGE_ACCESS: return R.string.guard_req_usage_access;
            case OVERLAY: return R.string.guard_req_overlay;
            default: return R.string.guard_req_notifications;
        }
    }
}
