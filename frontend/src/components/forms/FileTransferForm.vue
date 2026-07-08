<!--
    Copyright (c) 2023, 2024 Contributors to the Eclipse Foundation

    See the NOTICE file(s) distributed with this work for additional
    information regarding copyright ownership.

    This program and the accompanying materials are made available under the
    terms of the Eclipse Public License 2.0 which is available at
    http://www.eclipse.org/legal/epl-2.0

    SPDX-License-Identifier: EPL-2.0
 -->
<script setup lang="ts">
import { fileTypeMap } from '@/composables/file-types';
import axios from 'axios';
import { t } from '@/i18n/i18n';

defineProps({
  visible: Boolean
});

const emit = defineEmits(['update:visible', 'transferCompletionCallback']);

function _t(key: string, args?: any) {
  return t(`fileTransferForm.${key}`, args);
}

const params = reactive({
  jifaAnalysisRequest: ''
});

const form = ref();

// Transfer progress states
const processing = ref(false);
const transferId = ref<number | null>(null);
const percentage = ref(0);
const progressStatus = ref<{ status?: string }>({});
const message = ref('');

// Auto-detect the file type from the pasted analysis request JSON (its `type` field).
const detectedType = computed<string | null>(() => {
  const raw = params.jifaAnalysisRequest?.trim();
  if (!raw) {
    return null;
  }
  try {
    const obj = JSON.parse(raw);
    const type = obj?.type;
    return type && fileTypeMap.has(type) ? type : null;
  } catch {
    return null;
  }
});

const detectedTypeLabel = computed(() => {
  const type = detectedType.value;
  if (!type) {
    return null;
  }
  const ft = fileTypeMap.get(type);
  return ft ? t(`file.${ft.labelKey}`) : null;
});

// Something was pasted but it can't be parsed into a known type.
const analysisRequestInvalid = computed(() => {
  return !!params.jifaAnalysisRequest?.trim() && !detectedType.value;
});

const canSubmit = computed(() => !!detectedType.value && !processing.value);

function resetStates() {
  processing.value = false;
  transferId.value = null;
  percentage.value = 0;
  progressStatus.value = {};
  message.value = '';
}

function error(msg: string) {
  progressStatus.value = {
    status: 'exception'
  };
  message.value = msg;
  processing.value = false;
}

function doTransfer() {
  if (!detectedType.value) {
    return;
  }
  resetStates();
  processing.value = true;
  axios
    .post('/jifa-api/files/cratos/transfer', {
      type: detectedType.value,
      jifaAnalysisRequest: params.jifaAnalysisRequest.trim()
    })
    .then((resp) => {
      transferId.value = resp.data;
      queryProgress();
    })
    .catch((e) => {
      error(e.response?.data?.message ?? String(e));
    });
}

function queryProgress() {
  axios
    .get(`/jifa-api/files/transfer/${transferId.value}`)
    .then((resp) => {
      let progress = resp.data;
      let state = progress.state;
      if (state === 'IN_PROGRESS') {
        if (progress.totalSize > 0) {
          percentage.value = Math.floor((progress.transferredSize / progress.totalSize) * 100);
        }
        setTimeout(queryProgress, 1000);
      } else if (state === 'SUCCESS') {
        percentage.value = 100;
        progressStatus.value = {
          status: 'success'
        };
        nextTick(() => emit('transferCompletionCallback', progress.fileId));
      } else {
        error(progress.failureMessage);
      }
    })
    .catch((e) => {
      error(e.response?.data?.message ?? String(e));
    });
}

function close(done: any) {
  if (processing.value) {
    return;
  }
  done();
}
</script>
<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="(newValue: boolean) => $emit('update:visible', newValue)"
    :before-close="close"
    :title="t('file.new')"
    width="640px"
  >
    <el-form
      label-position="right"
      label-width="110px"
      size="large"
      :disabled="processing"
      hide-required-asterisk
      :model="params"
      ref="form"
    >
      <!-- Paste the cratos analysis request -->
      <el-form-item :label="_t('analysisRequest')">
        <el-input
          v-model="params.jifaAnalysisRequest"
          type="textarea"
          :placeholder="_t('analysisRequestPlaceholder')"
          :rows="10"
          resize="none"
          class="ej-file-transfer-form-input"
        />
      </el-form-item>

      <!-- Auto-detected file type -->
      <el-form-item :label="_t('detectedType')">
        <el-tag v-if="detectedType" type="success" size="large">{{ detectedTypeLabel }}</el-tag>
        <el-tag v-else-if="analysisRequestInvalid" type="danger" size="large">
          {{ _t('unrecognized') }}
        </el-tag>
        <span v-else style="color: var(--el-text-color-placeholder)">—</span>
      </el-form-item>

      <el-alert
        v-if="analysisRequestInvalid"
        style="margin-bottom: 12px"
        :title="_t('invalidAnalysisRequest')"
        type="error"
        :closable="false"
      />

      <el-form-item>
        <el-button type="primary" :disabled="!canSubmit" @click="doTransfer">
          {{ t('common.submit') }}
        </el-button>
      </el-form-item>
    </el-form>

    <el-progress
      style="margin-left: 110px; width: 420px"
      :stroke-width="15"
      text-inside
      striped
      striped-flow
      :duration="64"
      :percentage="percentage"
      v-bind="progressStatus"
      v-if="processing || progressStatus.status"
    />

    <div
      style="
        margin-left: 110px;
        margin-top: 7px;
        width: 420px;
        padding: 8px 16px;
        border-radius: var(--el-border-radius-small);
        font-size: 12px;
        word-break: break-all;
        background-color: var(--el-color-error-light-9);
        color: var(--el-color-error);
      "
      v-if="message"
    >
      {{ message }}
    </div>
  </el-dialog>
</template>

<style scoped>
.ej-file-transfer-form-input {
  width: 420px;
}
</style>
