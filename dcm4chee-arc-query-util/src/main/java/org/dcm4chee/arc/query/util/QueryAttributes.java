/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2017
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.query.util;

import com.querydsl.core.types.Order;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.dict.archive.ArchiveTag;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.AttributeSet;
import org.dcm4chee.arc.conf.AttributesBuilder;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2017
 */
public class QueryAttributes {
    private static ElementDictionary DICT = ElementDictionary.getStandardElementDictionary();

    private final Attributes keys = new Attributes();
    private final AttributesBuilder builder = new AttributesBuilder(keys);
    private boolean includeAll;

    private final ArrayList<OrderByTag> orderByTags = new ArrayList<>();

    public QueryAttributes(UriInfo info, Map<String, AttributeSet> attributeSetMap) {
        this(info.getQueryParameters(), attributeSetMap);
    }

    public QueryAttributes(MultivaluedMap<String, String> map, Map<String, AttributeSet> attributeSetMap) {
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            String key = entry.getKey();
            switch (key) {
                case "includefield":
                    addIncludeTag(entry.getValue(), attributeSetMap);
                    break;
                case "orderby":
                    addOrderByTag(entry.getValue());
                    break;
                case "accept":
                case "access_token":
                case "comparefield":
                case "count":
                case "different":
                case "missing":
                case "offset":
                case "limit":
                case "priority":
                case "withoutstudies":
                case "fuzzymatching":
                case "returnempty":
                case "expired":
                case "retrievefailed":
                case "compressionfailed":
                case "patientVerificationStatus":
                case "storageVerificationFailed":
                case "storageVerificationPolicy":
                case "storageVerificationUpdateLocationStatus":
                case "storageVerificationStorageID":
                case "incomplete":
                case "ExternalRetrieveAET":
                case "ExternalRetrieveAET!":
                case "only-stgcmt":
                case "only-ian":
                case "batchID":
                case "queue":
                case "SplitStudyDateRange":
                case "ForceQueryByStudyUID":
                case "includedefaults":
                case "ExpirationDate":
                    break;
                case "SendingApplicationEntityTitleOfSeries":
                    keys.setString(ArchiveTag.PrivateCreator, ArchiveTag.SendingApplicationEntityTitleOfSeries, VR.AE,
                            entry.getValue().toArray(StringUtils.EMPTY_STRING));
                    break;
                case "StudyReceiveDateTime":
                    keys.setString(ArchiveTag.PrivateCreator, ArchiveTag.StudyReceiveDateTime, VR.DT,
                            entry.getValue().toArray(StringUtils.EMPTY_STRING));
                    break;
                default:
                    addQueryKey(key, entry.getValue());
                    break;
            }
        }
    }

    private void addIncludeTag(List<String> includefields, Map<String, AttributeSet> attributeSetMap) {
        for (String s : includefields) {
            if (s.equals("all")) {
                includeAll = true;
                break;
            }
            for (String field : StringUtils.split(s, ','))
                if (!includeAttributeSet(s, attributeSetMap))
                    try {
                        int[] tagPath = TagUtils.parseTagPath(field);
                        builder.setNullIfAbsent(tagPath);
                    } catch (IllegalArgumentException e2) {
                        throw new IllegalArgumentException("includefield=" + s);
                    }
        }
    }

    private boolean includeAttributeSet(String includefield, Map<String, AttributeSet> attributeSetMap) {
        if (attributeSetMap != null) {
            AttributeSet attributeSet = attributeSetMap.get(includefield);
            if (attributeSet != null) {
                for (int tag : attributeSet.getSelection())
                    builder.setNullIfAbsent(tag);
                return true;
            }
        }
        return false;
    }

    public void addReturnTags(int... tags) {
        for (int tag : tags)
            builder.setNullIfAbsent(tag);
    }

    private void addOrderByTag(List<String> orderby) {
        for (String s : orderby) {
            try {
                for (String field : StringUtils.split(s, ',')) {
                    boolean desc = field.charAt(0) == '-';
                    int tags[] = TagUtils.parseTagPath(desc ? field.substring(1) : field);
                    orderByTags.add(new OrderByTag(tags[tags.length - 1], desc ? Order.DESC : Order.ASC));
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("orderby=" + s);
            }
        }
    }

    public boolean isIncludeAll() {
        return includeAll;
    }

    public Attributes getQueryKeys() {
        return keys;
    }

    public Attributes getReturnKeys(int[] includetags) {
        Attributes returnKeys = new Attributes(keys.size() + 4 + includetags.length);
        returnKeys.addAll(keys);
        returnKeys.setNull(Tag.SpecificCharacterSet, VR.CS);
        returnKeys.setNull(Tag.RetrieveAETitle, VR.AE);
        returnKeys.setNull(Tag.InstanceAvailability, VR.CS);
        returnKeys.setNull(Tag.TimezoneOffsetFromUTC, VR.SH);
        for (int tag : includetags)
            returnKeys.setNull(tag, DICT.vrOf(tag));
        return returnKeys;
    }
    
    public ArrayList<OrderByTag> getOrderByTags() {
        return orderByTags;
    }

    private void addQueryKey(String attrPath, List<String> values) {
        try {
            builder.setString(TagUtils.parseTagPath(attrPath), values.toArray(new String[values.size()]));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(attrPath + "=" + values.get(0));
        }
    }

}
