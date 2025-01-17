/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.opencastproject.index.service.catalog.adapter;

import org.opencastproject.index.service.exception.ListProviderException;
import org.opencastproject.index.service.resources.list.api.ListProvidersService;
import org.opencastproject.index.service.resources.list.query.ResourceListQueryImpl;
import org.opencastproject.metadata.dublincore.DCMIPeriod;
import org.opencastproject.metadata.dublincore.EncodingSchemeUtils;
import org.opencastproject.metadata.dublincore.MetadataCollection;
import org.opencastproject.metadata.dublincore.MetadataField;

import com.entwinemedia.fn.data.Opt;
import com.google.common.collect.Iterables;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DublinCoreMetadataCollection extends AbstractMetadataCollection {
  private static final Logger logger = LoggerFactory.getLogger(DublinCoreMetadataCollection.class);

  private Opt<Boolean> getCollectionIsTranslatable(MetadataField<?> metadataField,
          ListProvidersService listProvidersService) {
    if (listProvidersService != null && metadataField.getListprovider().isSome()) {
      try {
        boolean isTranslatable = listProvidersService.isTranslatable(metadataField.getListprovider().get());
        return Opt.some(isTranslatable);
      } catch (ListProviderException ex) {
        // failed to get is-translatable property on list-provider-service
        // as this field is optional, it is fine to pass here
      }
    }
    return Opt.none();
  }

  @Override
  public MetadataCollection getCopy() {
    MetadataCollection copiedCollection = new DublinCoreMetadataCollection();
    for (MetadataField field : getFields()) {
      MetadataField copiedField = new MetadataField(field);
      copiedCollection.addField(copiedField);
    }
    return copiedCollection;
  }

  private String getCollectionDefault(MetadataField<?> metadataField,
          ListProvidersService listProvidersService) {
    if (listProvidersService != null && metadataField.getListprovider().isSome()) {
      try {
        return listProvidersService.getDefault(metadataField.getListprovider().get());

      } catch (ListProviderException ex) {
        // failed to get default property on list-provider-service
        // as this field is optional, it is fine to pass here
      }
    }
    return null;
  }

  private Opt<Map<String, String>> getCollection(MetadataField<?> metadataField,
          ListProvidersService listProvidersService) {
    try {
      if (listProvidersService != null && metadataField.getListprovider().isSome()) {
        Map<String, String> collection = listProvidersService.getList(metadataField.getListprovider().get(),
                new ResourceListQueryImpl(), true);
        if (collection != null) {
          return Opt.some(collection);
        }
      }
      return Opt.<Map<String, String>> none();
    } catch (ListProviderException e) {
      logger.warn("Unable to set collection on metadata because {}", ExceptionUtils.getStackTrace(e));
      return Opt.<Map<String, String>> none();
    }
  }

  public void addField(MetadataField<?> metadataField, String value, ListProvidersService listProvidersService) {
    addField(metadataField, Collections.singletonList(value), listProvidersService);
  }

  public void addField(MetadataField<?> metadataField, ListProvidersService listProvidersService) {
    addField(metadataField, Collections.emptyList(), listProvidersService);
  }

  public void addField(MetadataField<?> metadataField, List<String> values, ListProvidersService listProvidersService) {

    List<String> filteredValues = values.stream()
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());

    String defaultKey = getCollectionDefault(metadataField, listProvidersService);

    if (StringUtils.isNotBlank(defaultKey) && filteredValues.isEmpty()) {
      filteredValues.add(defaultKey);
    }

    if (filteredValues.size() > 1
            && metadataField.getType() != MetadataField.Type.MIXED_TEXT
            && metadataField.getType() != MetadataField.Type.ITERABLE_TEXT) {
      logger.warn("Cannot put multiple values into a single-value field, only the last value is used. {}",
              Arrays.toString(filteredValues.toArray()));
    }

    switch (metadataField.getType()) {
      case BOOLEAN:
        MetadataField<Boolean> booleanField = MetadataField.createBooleanMetadata(metadataField.getInputID(),
                Opt.some(metadataField.getOutputID()), metadataField.getLabel(), metadataField.isReadOnly(),
                metadataField.isRequired(), metadataField.getOrder(), metadataField.getNamespace());
        if (!filteredValues.isEmpty()) {
          booleanField.setValue(Boolean.parseBoolean(Iterables.getLast(filteredValues)));
        }
        addField(booleanField);
        break;
      case DATE:
        if (metadataField.getPattern().isNone()) {
          metadataField.setPattern(Opt.some("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        }
        MetadataField<Date> dateField = MetadataField.createDateMetadata(metadataField.getInputID(),
                Opt.some(metadataField.getOutputID()), metadataField.getLabel(), metadataField.isReadOnly(),
                metadataField.isRequired(), metadataField.getPattern().get(), metadataField.getOrder(),
                metadataField.getNamespace());
        if (!filteredValues.isEmpty()) {
          dateField.setValue(EncodingSchemeUtils.decodeDate(Iterables.getLast(filteredValues)));
        }
        addField(dateField);
        break;
      case DURATION:
        MetadataField<String> durationField = MetadataField.createDurationMetadataField(metadataField.getInputID(),
                Opt.some(metadataField.getOutputID()), metadataField.getLabel(), metadataField.isReadOnly(),
                metadataField.isRequired(), getCollectionIsTranslatable(metadataField, listProvidersService),
                getCollection(metadataField, listProvidersService),
                metadataField.getCollectionID(), metadataField.getOrder(), metadataField.getNamespace());

        if (!filteredValues.isEmpty()) {
          String value = Iterables.getLast(filteredValues);
          DCMIPeriod period = EncodingSchemeUtils.decodePeriod(value);
          Long longValue = -1L;

          // Check to see if it is from the front end
          String[] durationParts = value.split(":");
          if (durationParts.length == 3) {
            Integer hours = Integer.parseInt(durationParts[0]);
            Integer minutes = Integer.parseInt(durationParts[1]);
            Integer seconds = Integer.parseInt(durationParts[2]);
            longValue = ((hours.longValue() * 60 + minutes.longValue()) * 60 + seconds.longValue()) * 1000;
          } else if (period != null && period.hasStart() && period.hasEnd()) {
            longValue = period.getEnd().getTime() - period.getStart().getTime();
          } else {
            try {
              longValue = Long.parseLong(value);
            } catch (NumberFormatException e) {
              logger.debug("Unable to parse duration '{}' value as either a period or millisecond duration.", value);
              longValue = -1L;
            }
          }
          if (longValue > 0) {
            durationField.setValue(longValue.toString());
          }
        }
        addField(durationField);
        break;
      case ITERABLE_TEXT:
        // Add an iterable text style field
        MetadataField<Iterable<String>> iterableTextField = MetadataField.createIterableStringMetadataField(
                metadataField.getInputID(), Opt.some(metadataField.getOutputID()), metadataField.getLabel(),
                metadataField.isReadOnly(), metadataField.isRequired(),
                getCollectionIsTranslatable(metadataField, listProvidersService),
                getCollection(metadataField, listProvidersService), metadataField.getCollectionID(),
                metadataField.getDelimiter(), metadataField.getOrder(), metadataField.getNamespace());
        if (!filteredValues.isEmpty()) {
          iterableTextField.setValue(filteredValues);
        }
        addField(iterableTextField);
        break;
      case MIXED_TEXT:
        // Add an iterable text style field
        MetadataField<Iterable<String>> mixedIterableTextField = MetadataField.createMixedIterableStringMetadataField(
                metadataField.getInputID(), Opt.some(metadataField.getOutputID()), metadataField.getLabel(),
                metadataField.isReadOnly(), metadataField.isRequired(),
                getCollectionIsTranslatable(metadataField, listProvidersService),
                getCollection(metadataField, listProvidersService), metadataField.getCollectionID(),
                metadataField.getDelimiter(), metadataField.getOrder(), metadataField.getNamespace());
        if (!filteredValues.isEmpty()) {
          mixedIterableTextField.setValue(filteredValues);
        }
        addField(mixedIterableTextField);
        break;
      case LONG:
        MetadataField<Long> longField = MetadataField.createLongMetadataField(metadataField.getInputID(),
                Opt.some(metadataField.getOutputID()), metadataField.getLabel(), metadataField.isReadOnly(),
                metadataField.isRequired(), getCollectionIsTranslatable(metadataField, listProvidersService),
                getCollection(metadataField, listProvidersService),
                metadataField.getCollectionID(), metadataField.getOrder(), metadataField.getNamespace());
        if (!filteredValues.isEmpty()) {
          longField.setValue(Long.parseLong(Iterables.getLast(filteredValues)));
        }
        addField(longField);
        break;
      case TEXT:
        MetadataField<String> textField = MetadataField.createTextMetadataField(metadataField.getInputID(),
                Opt.some(metadataField.getOutputID()), metadataField.getLabel(), metadataField.isReadOnly(),
                metadataField.isRequired(), getCollectionIsTranslatable(metadataField, listProvidersService),
                getCollection(metadataField, listProvidersService),
                metadataField.getCollectionID(), metadataField.getOrder(), metadataField.getNamespace());
        if (!filteredValues.isEmpty()) {
          textField.setValue(Iterables.getLast(filteredValues));
        }
        addField(textField);
        break;
      case TEXT_LONG:
        MetadataField<String> textLongField = MetadataField.createTextLongMetadataField(metadataField.getInputID(),
                Opt.some(metadataField.getOutputID()), metadataField.getLabel(), metadataField.isReadOnly(),
                metadataField.isRequired(), getCollectionIsTranslatable(metadataField, listProvidersService),
                getCollection(metadataField, listProvidersService),
                metadataField.getCollectionID(), metadataField.getOrder(), metadataField.getNamespace());
        if (!filteredValues.isEmpty()) {
          textLongField.setValue(Iterables.getLast(filteredValues));
        }
        addField(textLongField);
        break;
      case START_DATE:
        if (metadataField.getPattern().isNone()) {
          metadataField.setPattern(Opt.some("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        }
        MetadataField<String> startDate = MetadataField.createTemporalStartDateMetadata(metadataField.getInputID(),
                Opt.some(metadataField.getOutputID()), metadataField.getLabel(), metadataField.isReadOnly(),
                metadataField.isRequired(), metadataField.getPattern().get(), metadataField.getOrder(),
                metadataField.getNamespace());
        if (!filteredValues.isEmpty()) {
          startDate.setValue(Iterables.getLast(filteredValues));
        }
        addField(startDate);
        break;
      case ORDERED_TEXT:
        MetadataField<String> orderedTextField = MetadataField.createOrderedTextMetadataField(metadataField.getInputID(),
            Opt.some(metadataField.getOutputID()), metadataField.getLabel(), metadataField.isReadOnly(),
            metadataField.isRequired(), getCollectionIsTranslatable(metadataField, listProvidersService),
            getCollection(metadataField, listProvidersService),
            metadataField.getCollectionID(), metadataField.getOrder(), metadataField.getNamespace());
        if (!filteredValues.isEmpty()) {
          orderedTextField.setValue(Iterables.getLast(filteredValues));
        }
        addField(orderedTextField);
        break;
      default:
        throw new IllegalArgumentException("Unknown metadata type! " + metadataField.getType());
    }
  }
}
