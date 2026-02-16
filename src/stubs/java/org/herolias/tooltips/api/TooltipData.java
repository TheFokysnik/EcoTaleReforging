package org.herolias.tooltips.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stub — DynamicTooltipsLib TooltipData.
 */
public final class TooltipData {

    private final List<String> lines;
    private final String nameOverride;
    private final String descriptionOverride;
    private final String nameTranslationKey;
    private final String descriptionTranslationKey;
    private final String stableHashInput;

    private TooltipData(Builder builder) {
        this.lines = Collections.unmodifiableList(new ArrayList<>(builder.lines));
        this.nameOverride = builder.nameOverride;
        this.descriptionOverride = builder.descriptionOverride;
        this.nameTranslationKey = builder.nameTranslationKey;
        this.descriptionTranslationKey = builder.descriptionTranslationKey;
        this.stableHashInput = builder.stableHashInput;
    }

    @Nonnull public List<String> getLines() { return lines; }
    @Nullable public String getNameOverride() { return nameOverride; }
    @Nullable public String getDescriptionOverride() { return descriptionOverride; }
    @Nullable public String getNameTranslationKey() { return nameTranslationKey; }
    @Nullable public String getDescriptionTranslationKey() { return descriptionTranslationKey; }
    @Nonnull public String getStableHashInput() { return stableHashInput; }

    public boolean isAdditive() {
        return nameOverride == null && descriptionOverride == null
                && nameTranslationKey == null && descriptionTranslationKey == null;
    }

    public boolean isEmpty() {
        return lines.isEmpty() && nameOverride == null && descriptionOverride == null
                && nameTranslationKey == null && descriptionTranslationKey == null;
    }

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<String> lines = new ArrayList<>();
        private String nameOverride;
        private String descriptionOverride;
        private String nameTranslationKey;
        private String descriptionTranslationKey;
        private String stableHashInput = "";

        private Builder() {}

        @Nonnull public Builder addLine(@Nonnull String line) {
            this.lines.add(line);
            return this;
        }

        @Nonnull public Builder addLines(@Nonnull List<String> lines) {
            this.lines.addAll(lines);
            return this;
        }

        @Nonnull public Builder nameOverride(@Nonnull String name) {
            this.nameOverride = name;
            return this;
        }

        @Nonnull public Builder nameTranslationKey(@Nonnull String key) {
            this.nameTranslationKey = key;
            return this;
        }

        @Nonnull public Builder descriptionOverride(@Nonnull String description) {
            this.descriptionOverride = description;
            return this;
        }

        @Nonnull public Builder descriptionTranslationKey(@Nonnull String key) {
            this.descriptionTranslationKey = key;
            return this;
        }

        @Nonnull public Builder hashInput(@Nonnull String hashInput) {
            this.stableHashInput = hashInput;
            return this;
        }

        @Nonnull public TooltipData build() {
            return new TooltipData(this);
        }
    }
}
