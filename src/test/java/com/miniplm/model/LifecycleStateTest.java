package com.miniplm.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleStateTest {

    @Test
    void followsTheNormalPath() {
        assertThat(LifecycleState.IN_WORK.canMoveTo(LifecycleState.UNDER_REVIEW)).isTrue();
        assertThat(LifecycleState.UNDER_REVIEW.canMoveTo(LifecycleState.APPROVED)).isTrue();
        assertThat(LifecycleState.APPROVED.canMoveTo(LifecycleState.RELEASED)).isTrue();
    }

    @Test
    void aRejectedReviewGoesBackToInWork() {
        assertThat(LifecycleState.UNDER_REVIEW.canMoveTo(LifecycleState.IN_WORK)).isTrue();
    }

    @Test
    void statesCannotBeSkipped() {
        assertThat(LifecycleState.IN_WORK.canMoveTo(LifecycleState.APPROVED)).isFalse();
        assertThat(LifecycleState.IN_WORK.canMoveTo(LifecycleState.RELEASED)).isFalse();
        assertThat(LifecycleState.UNDER_REVIEW.canMoveTo(LifecycleState.RELEASED)).isFalse();
    }

    @Test
    void approvedCannotGoBackToInWork() {
        assertThat(LifecycleState.APPROVED.canMoveTo(LifecycleState.IN_WORK)).isFalse();
    }

    @Test
    void releasedIsFinal() {
        for (LifecycleState target : LifecycleState.values()) {
            assertThat(LifecycleState.RELEASED.canMoveTo(target)).isFalse();
        }
    }

    @Test
    void onlyInWorkCanBeEdited() {
        assertThat(LifecycleState.IN_WORK.isEditable()).isTrue();
        assertThat(LifecycleState.UNDER_REVIEW.isEditable()).isFalse();
        assertThat(LifecycleState.APPROVED.isEditable()).isFalse();
        assertThat(LifecycleState.RELEASED.isEditable()).isFalse();
    }
}
