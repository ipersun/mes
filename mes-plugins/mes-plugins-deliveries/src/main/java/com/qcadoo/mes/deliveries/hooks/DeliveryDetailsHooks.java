/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo MES
 * Version: 1.3
 *
 * This file is part of Qcadoo.
 *
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */
package com.qcadoo.mes.deliveries.hooks;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.qcadoo.mes.basic.ParameterService;
import com.qcadoo.mes.basic.util.CurrencyService;
import com.qcadoo.mes.deliveries.DeliveriesService;
import com.qcadoo.mes.deliveries.constants.CompanyFieldsD;
import com.qcadoo.mes.deliveries.constants.DeliveriesConstants;
import com.qcadoo.mes.deliveries.constants.DeliveryFields;
import com.qcadoo.mes.deliveries.states.constants.DeliveryState;
import com.qcadoo.mes.deliveries.states.constants.DeliveryStateChangeFields;
import com.qcadoo.mes.states.constants.StateChangeStatus;
import com.qcadoo.mes.states.service.client.util.StateChangeHistoryService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.CustomRestriction;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.GridComponent;
import com.qcadoo.view.api.components.LookupComponent;
import com.qcadoo.view.api.components.WindowComponent;
import com.qcadoo.view.api.ribbon.RibbonActionItem;
import com.qcadoo.view.api.ribbon.RibbonGroup;
import com.qcadoo.view.api.utils.NumberGeneratorService;

@Service
public class DeliveryDetailsHooks {

    private static final String L_FORM = "form";

    private static final String L_WINDOW = "window";

    private static final String L_RELATED_DELIVERY = "relatedDelivery";

    private static final String L_CREATE_RELATED_DELIVERY = "createRelatedDelivery";

    private static final String L_SHOW_RELATED_DELIVERIES = "showRelatedDeliveries";

    private static final String L_COPY_ORDERED_PRODUCTS_TO_DELIVERY = "copyOrderedProductsToDelivered";

    private static final String L_COPY_PRODUCTS_WITHOUT_QUANTITY = "copyProductsWithoutQuantityAndPrice";

    private static final String L_COPY_PRODUCTS_WITH_QUANTITY = "copyProductsWithQuantityAndPrice";

    @Autowired
    private DeliveriesService deliveriesService;

    @Autowired
    private NumberGeneratorService numberGeneratorService;

    @Autowired
    private StateChangeHistoryService stateChangeHistoryService;

    @Autowired
    private CurrencyService currencyService;

    @Autowired
    private ParameterService parameterService;

    public void generateDeliveryNumber(final ViewDefinitionState view) {
        numberGeneratorService.generateAndInsertNumber(view, DeliveriesConstants.PLUGIN_IDENTIFIER,
                DeliveriesConstants.MODEL_DELIVERY, L_FORM, DeliveryFields.NUMBER);
    }

    public void fillCompanyFieldsForSupplier(final ViewDefinitionState view) {
        LookupComponent supplierLookup = (LookupComponent) view.getComponentByReference(DeliveryFields.SUPPLIER);
        FieldComponent deliveryDateBufferField = (FieldComponent) view
                .getComponentByReference(DeliveryFields.DELIVERY_DATE_BUFFER);
        FieldComponent paymentFormField = (FieldComponent) view.getComponentByReference(DeliveryFields.PAYMENT_FORM);

        Entity supplier = supplierLookup.getEntity();

        if (supplier == null) {
            deliveryDateBufferField.setFieldValue(null);
            paymentFormField.setFieldValue(null);
        } else {
            deliveryDateBufferField.setFieldValue(supplier.getIntegerField(CompanyFieldsD.BUFFER));
            paymentFormField.setFieldValue(supplier.getStringField(CompanyFieldsD.PAYMENT_FORM));
        }

        deliveryDateBufferField.requestComponentUpdateState();
        paymentFormField.requestComponentUpdateState();
    }

    public void changeFieldsEnabledDependOnState(final ViewDefinitionState view) {
        FormComponent deliveryForm = (FormComponent) view.getComponentByReference(L_FORM);

        FieldComponent stateField = (FieldComponent) view.getComponentByReference(DeliveryFields.STATE);
        String state = stateField.getFieldValue().toString();

        if (deliveryForm.getEntityId() == null) {
            changeFieldsEnabled(view, true, false, false);
        } else {
            if (DeliveryState.PREPARED.getStringValue().equals(state) || DeliveryState.APPROVED.getStringValue().equals(state)) {
                changeFieldsEnabled(view, false, false, true);
            } else if (DeliveryState.DECLINED.getStringValue().equals(state)
                    || DeliveryState.RECEIVED.getStringValue().equals(state)
                    || DeliveryState.RECEIVE_CONFIRM_WAITING.getStringValue().equals(state)) {
                changeFieldsEnabled(view, false, false, false);
            } else {
                changeFieldsEnabled(view, true, true, true);
            }
        }
    }

    private void changeFieldsEnabled(final ViewDefinitionState view, final boolean enabledForm, final boolean enabledOrderedGrid,
            final boolean enabledDeliveredGrid) {
        FormComponent deliveryForm = (FormComponent) view.getComponentByReference(L_FORM);

        GridComponent orderedProducts = (GridComponent) view.getComponentByReference(DeliveryFields.ORDERED_PRODUCTS);
        GridComponent deliveredProducts = (GridComponent) view.getComponentByReference(DeliveryFields.DELIVERED_PRODUCTS);

        deliveryForm.setFormEnabled(enabledForm);
        orderedProducts.setEnabled(enabledOrderedGrid);
        orderedProducts.setEditable(enabledOrderedGrid);
        deliveredProducts.setEnabled(enabledDeliveredGrid);
        deliveredProducts.setEditable(enabledDeliveredGrid);
    }

    public void fillDeliveryAddressDefaultValue(final ViewDefinitionState view) {
        FormComponent form = (FormComponent) view.getComponentByReference(L_FORM);
        if (form.getEntityId() != null) {
            return;
        }

        FieldComponent deliveryAddressField = (FieldComponent) view.getComponentByReference(DeliveryFields.DELIVERY_ADDRESS);
        String deliveryAddress = (String) deliveryAddressField.getFieldValue();

        if (StringUtils.isEmpty(deliveryAddress)) {
            deliveryAddressField.setFieldValue(deliveriesService.getDeliveryAddressDefaultValue());
        }
    }

    public void fillDescriptionDefaultValue(final ViewDefinitionState view) {
        FormComponent deliveryForm = (FormComponent) view.getComponentByReference(L_FORM);

        if (deliveryForm.getEntityId() != null) {
            return;
        }

        FieldComponent descriptionField = (FieldComponent) view.getComponentByReference(DeliveryFields.DESCRIPTION);
        String description = (String) descriptionField.getFieldValue();

        if (StringUtils.isEmpty(description)) {
            descriptionField.setFieldValue(deliveriesService.getDescriptionDefaultValue());
        }
    }

    public void filterStateChangeHistory(final ViewDefinitionState view) {
        final GridComponent historyGrid = (GridComponent) view.getComponentByReference("loggingsGrid");
        final CustomRestriction onlySuccessfulRestriction = stateChangeHistoryService.buildStatusRestriction(
                DeliveryStateChangeFields.STATUS, Lists.newArrayList(StateChangeStatus.SUCCESSFUL.getStringValue()));
        historyGrid.setCustomRestriction(onlySuccessfulRestriction);
    }

    public void updateRelatedDeliveryButtonsState(final ViewDefinitionState view) {
        FormComponent deliveryForm = (FormComponent) view.getComponentByReference(L_FORM);
        Long deliveryId = deliveryForm.getEntityId();

        WindowComponent window = (WindowComponent) view.getComponentByReference(L_WINDOW);
        RibbonGroup reports = (RibbonGroup) window.getRibbon().getGroupByName(L_RELATED_DELIVERY);

        RibbonActionItem createRelatedDelivery = (RibbonActionItem) reports.getItemByName(L_CREATE_RELATED_DELIVERY);
        RibbonActionItem showRelatedDelivery = (RibbonActionItem) reports.getItemByName(L_SHOW_RELATED_DELIVERIES);

        if (deliveryId == null) {
            return;
        }

        Entity delivery = deliveriesService.getDelivery(deliveryId);
        List<Entity> relatedDeliveries = delivery.getHasManyField(DeliveryFields.RELATED_DELIVERIES);

        boolean received = DeliveryState.RECEIVED.getStringValue().equals(delivery.getStringField(DeliveryFields.STATE));
        boolean receiveConfirmWaiting = DeliveryState.RECEIVE_CONFIRM_WAITING.getStringValue().equals(
                delivery.getStringField(DeliveryFields.STATE));
        boolean created = ((relatedDeliveries != null) && !relatedDeliveries.isEmpty());

        updateButtonState(createRelatedDelivery, (received || receiveConfirmWaiting) && !created);
        updateButtonState(showRelatedDelivery, (received || receiveConfirmWaiting) && created);
    }

    private void updateButtonState(final RibbonActionItem ribbonActionItem, final boolean isEnabled) {
        ribbonActionItem.setEnabled(isEnabled);
        ribbonActionItem.requestUpdate(true);
    }

    public void fillCurrencyFields(final ViewDefinitionState view) {
        List<String> referenceNames = Lists.newArrayList("deliveredProductsCumulatedTotalPriceCurrency",
                "orderedProductsCumulatedTotalPriceCurrency");
        FormComponent form = (FormComponent) view.getComponentByReference(L_FORM);
        Entity delivery = form.getEntity();
        deliveriesService.fillCurrencyFieldsForDelivery(view, referenceNames, delivery);

        LookupComponent currency = (LookupComponent) view.getComponentByReference(DeliveryFields.CURRENCY);
        if (currency.getFieldValue() == null && form.getEntityId() == null) {
            Entity currencyEntity = currencyService.getCurrentCurrency();
            currency.setFieldValue(currencyEntity.getId());
            currency.requestComponentUpdateState();
        }

    }

    public void disableShowProductButton(final ViewDefinitionState view) {
        deliveriesService.disableShowProductButton(view);
    }

    public void fillLocationDefaultValue(final ViewDefinitionState view) {
        FormComponent deliveryForm = (FormComponent) view.getComponentByReference(L_FORM);

        if (deliveryForm.getEntityId() != null) {
            return;
        }

        LookupComponent locationField = (LookupComponent) view.getComponentByReference(DeliveryFields.LOCATION);
        Entity location = locationField.getEntity();

        if (location == null) {
            Entity defaultLocation = parameterService.getParameter().getBelongsToField(DeliveryFields.LOCATION);

            if (defaultLocation == null) {
                locationField.setFieldValue(null);
            } else {
                locationField.setFieldValue(defaultLocation.getId());
            }
            locationField.requestComponentUpdateState();
        }
    }

    public void changeLocationEnabledDependOnState(final ViewDefinitionState view) {
        FormComponent deliveryForm = (FormComponent) view.getComponentByReference(L_FORM);

        LookupComponent locationField = (LookupComponent) view.getComponentByReference(DeliveryFields.LOCATION);

        if (deliveryForm.getEntityId() == null) {
            locationField.setEnabled(true);
        } else {
            FieldComponent stateField = (FieldComponent) view.getComponentByReference(DeliveryFields.STATE);
            String state = stateField.getFieldValue().toString();
            if (DeliveryState.DECLINED.getStringValue().equals(state) || DeliveryState.RECEIVED.getStringValue().equals(state)
                    || DeliveryState.RECEIVE_CONFIRM_WAITING.getStringValue().equals(state)) {
                locationField.setEnabled(false);
            } else {
                locationField.setEnabled(true);
            }
        }
    }

    public void updateCopyOrderedProductButtonsState(final ViewDefinitionState view) {
        FormComponent deliveryForm = (FormComponent) view.getComponentByReference(L_FORM);
        Long deliveryId = deliveryForm.getEntityId();

        WindowComponent window = (WindowComponent) view.getComponentByReference(L_WINDOW);
        RibbonGroup reports = (RibbonGroup) window.getRibbon().getGroupByName(L_COPY_ORDERED_PRODUCTS_TO_DELIVERY);

        RibbonActionItem copyWithout = (RibbonActionItem) reports.getItemByName(L_COPY_PRODUCTS_WITHOUT_QUANTITY);
        RibbonActionItem copyWith = (RibbonActionItem) reports.getItemByName(L_COPY_PRODUCTS_WITH_QUANTITY);

        if (deliveryId == null) {
            return;
        }

        Entity delivery = deliveriesService.getDelivery(deliveryId);
        boolean hasOrderedProducts = !delivery.getHasManyField(DeliveryFields.ORDERED_PRODUCTS).isEmpty();

        copyWith.setEnabled(hasOrderedProducts);
        copyWithout.setEnabled(hasOrderedProducts);
        copyWith.requestUpdate(true);
        copyWithout.requestUpdate(true);

    }

}
