package com.chris64233.loancovenant.domain;

/**
 * 财务快照生命周期。
 */
public enum SnapshotStatus {
    /** 当前最新版本，可被提款引用。 */
    CURRENT,
    /** 已被同一报告期的新版本追加取代，仅保留作历史判断依据。 */
    SUPERSEDED
}
