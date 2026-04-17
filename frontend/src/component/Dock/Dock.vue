<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, defineComponent, h } from 'vue';
import { useMotionValue, useSpring, useTransform } from 'motion-v';

export type SpringOptions = NonNullable<Parameters<typeof useSpring>[1]>;

export type DockItemData = {
  icon: unknown;
  label: unknown;
  onClick: () => void;
  className?: string;
  /** 为 true 时在按钮右上角显示红点，并带呼吸缩放动画（如新算法题提醒） */
  notify?: boolean;
};

export type DockProps = {
  items: DockItemData[];
  className?: string;
  distance?: number;
  panelHeight?: number;
  baseItemSize?: number;
  dockHeight?: number;
  magnification?: number;
  spring?: SpringOptions;
};

const props = withDefaults(defineProps<DockProps>(), {
  className: '',
  distance: 200,
  panelHeight: 64,
  baseItemSize: 50,
  dockHeight: 256,
  magnification: 70,
  spring: () => ({ mass: 0.1, stiffness: 150, damping: 12 })
});

const mouseX = useMotionValue(Infinity);
const isHovered = useMotionValue(0);
const currentHeight = ref(props.panelHeight);

const maxHeight = computed(() => Math.max(props.dockHeight, props.magnification + props.magnification / 2 + 4));

const heightRow = useTransform(isHovered, [0, 1], [props.panelHeight, maxHeight.value]);
const height = useSpring(heightRow, props.spring);

let unsubscribeHeight: (() => void) | null = null;

onMounted(() => {
  unsubscribeHeight = height.on('change', (latest: number) => {
    currentHeight.value = latest;
  });
});

onUnmounted(() => {
  if (unsubscribeHeight) {
    unsubscribeHeight();
  }
});

const handleMouseMove = (event: MouseEvent) => {
  isHovered.set(1);
  mouseX.set(event.pageX);
};

const handleMouseLeave = () => {
  isHovered.set(0);
  mouseX.set(Infinity);
};
</script>

<template>
  <div :style="{ height: currentHeight + 'px', scrollbarWidth: 'none' }" class="flex items-center mx-2 max-w-full">
    <div
      @mousemove="handleMouseMove"
      @mouseleave="handleMouseLeave"
      :class="`${props.className} dock-panel absolute bottom-2 left-1/2 transform -translate-x-1/2 flex items-end w-fit gap-4 rounded-2xl pb-2 px-4`"
      :style="{ height: props.panelHeight + 'px' }"
      role="toolbar"
      aria-label="Application dock"
    >
      <DockItem
        v-for="(item, index) in props.items"
        :key="index"
        :onClick="item.onClick"
        :className="item.className"
        :mouseX="mouseX"
        :spring="props.spring"
        :distance="props.distance"
        :magnification="props.magnification"
        :baseItemSize="props.baseItemSize"
        :item="item"
      />
    </div>
  </div>
</template>

<script lang="ts">
const DockItem = defineComponent({
  name: 'DockItem',
  props: {
    className: {
      type: String,
      default: ''
    },
    onClick: {
      type: Function,
      default: () => {}
    },
    mouseX: {
      type: Object as () => ReturnType<typeof useMotionValue<number>>,
      required: true
    },
    spring: {
      type: Object as () => SpringOptions,
      required: true
    },
    distance: {
      type: Number,
      required: true
    },
    baseItemSize: {
      type: Number,
      required: true
    },
    magnification: {
      type: Number,
      required: true
    },
    item: {
      type: Object as () => DockItemData,
      required: true
    }
  },
  setup(props) {
    const itemRef = ref<HTMLDivElement>();
    const isHovered = useMotionValue(0);
    const currentSize = ref(props.baseItemSize);

    const mouseDistance = useTransform(props.mouseX, (val: number) => {
      const rect = itemRef.value?.getBoundingClientRect() ?? {
        x: 0,
        width: props.baseItemSize
      };
      return val - rect.x - props.baseItemSize / 2;
    });

    const targetSize = useTransform(
      mouseDistance,
      [-props.distance, 0, props.distance],
      [props.baseItemSize, props.magnification, props.baseItemSize]
    );
    const size = useSpring(targetSize, props.spring);

    let unsubscribeSize: (() => void) | null = null;

    onMounted(() => {
      unsubscribeSize = size.on('change', (latest: number) => {
        currentSize.value = latest;
      });
    });

    onUnmounted(() => {
      if (unsubscribeSize) {
        unsubscribeSize();
      }
    });

    const handleHoverStart = () => isHovered.set(1);
    const handleHoverEnd = () => isHovered.set(0);
    const handleFocus = () => isHovered.set(1);
    const handleBlur = () => isHovered.set(0);

    return {
      itemRef,
      size,
      currentSize,
      isHovered,
      handleHoverStart,
      handleHoverEnd,
      handleFocus,
      handleBlur
    };
  },
  render() {
    const icon = typeof this.item.icon === 'function' ? this.item.icon() : this.item.icon;
    const label = typeof this.item.label === 'function' ? this.item.label() : this.item.label;

    return h(
      'div',
      {
        ref: 'itemRef',
        style: {
          width: this.currentSize + 'px',
          height: this.currentSize + 'px'
        },
        onMouseenter: this.handleHoverStart,
        onMouseleave: this.handleHoverEnd,
        onFocus: this.handleFocus,
        onBlur: this.handleBlur,
        onClick: this.onClick,
        class: `dock-item relative cursor-pointer inline-flex items-center justify-center rounded-xl shadow-md ${this.className}`,
        tabindex: 0,
        role: 'button',
        'aria-haspopup': 'true',
        'aria-label': this.item.notify ? `${typeof label === 'string' ? label : 'Dock'}，有新题目` : undefined
      },
      [
        h(DockIcon, {}, () => [icon]),
        h(DockLabel, { isHovered: this.isHovered }, () => [typeof label === 'string' ? label : label]),
        this.item.notify
          ? h('span', {
              class: 'dock-notify-dot',
              'aria-hidden': 'true'
            })
          : null
      ].filter(Boolean)
    );
  }
});

const DockLabel = defineComponent({
  name: 'DockLabel',
  props: {
    className: {
      type: String,
      default: ''
    },
    isHovered: {
      type: Object as () => ReturnType<typeof useMotionValue<number>>,
      required: true
    }
  },
  setup(props) {
    const isVisible = ref(false);

    let unsubscribe: (() => void) | null = null;

    onMounted(() => {
      unsubscribe = props.isHovered.on('change', (latest: number) => {
        isVisible.value = latest === 1;
      });
    });

    onUnmounted(() => {
      if (unsubscribe) {
        unsubscribe();
      }
    });

    return {
      isVisible
    };
  },
  render() {
    return h(
      'div',
      {
        class: `${this.className} dock-label absolute -top-8 left-1/2 w-fit whitespace-pre rounded-md px-2 py-0.5 text-xs transition-all duration-200`,
        role: 'tooltip',
        style: {
          transform: 'translateX(-50%)',
          opacity: this.isVisible ? 1 : 0,
          visibility: this.isVisible ? 'visible' : 'hidden'
        }
      },
      this.$slots.default?.()
    );
  }
});

const DockIcon = defineComponent({
  name: 'DockIcon',
  props: {
    className: {
      type: String,
      default: ''
    }
  },
  render() {
    return h(
      'div',
      {
        class: `flex items-center justify-center ${this.className}`
      },
      this.$slots.default?.()
    );
  }
});

export default defineComponent({
  name: 'AppDock',
  components: {
    DockItem
  }
});
</script>

<!-- 非 scoped：DockItem 由 render 函数生成，需全局类名 -->
<style>
@keyframes dock-notify-breathe {
  0%,
  100% {
    transform: scale(1);
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.5);
  }
  50% {
    transform: scale(1.4);
    box-shadow: 0 0 0 8px rgba(239, 68, 68, 0);
  }
}

.dock-notify-dot {
  position: absolute;
  top: 5px;
  right: 5px;
  width: 10px;
  height: 10px;
  border-radius: 9999px;
  background: var(--app-danger);
  border: 2px solid var(--app-surface-strong);
  pointer-events: none;
  z-index: 3;
  animation: dock-notify-breathe 1.6s ease-in-out infinite;
}

.dock-panel {
  background: var(--app-overlay);
  border: 1px solid var(--app-border-strong);
  box-shadow: var(--app-shadow);
  backdrop-filter: blur(14px);
}

.dock-item {
  background: var(--app-surface);
  border: 1px solid var(--app-border-strong);
  color: var(--app-text);
}

.dock-item:hover {
  background: var(--app-surface-strong);
}

.dock-item:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--app-accent) 35%, transparent);
  outline-offset: 2px;
}

.dock-label {
  border: 1px solid var(--app-border-strong);
  background: var(--app-surface);
  color: var(--app-text);
}
</style>
