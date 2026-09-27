package com.chris64233.loancovenant.domain;

/**
 * 财务快照版本状态。
 */
public enum SnapshotStatus {
    /** 当前有效版本。 */
    ACTIVE,
    /** 已被同一报告期的新版本更正（追加替代），原始数据保留不删除。 */
    SUPERSEDED
}
