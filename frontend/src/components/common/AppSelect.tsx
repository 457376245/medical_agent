"use client";

import { useCallback, useEffect, useId, useMemo, useRef, useState, type CSSProperties, type KeyboardEvent } from "react";
import { createPortal } from "react-dom";

export type AppSelectOption = {
  value: string;
  label: string;
  disabled?: boolean;
};

type AppSelectProps = {
  ariaLabel: string;
  value: string;
  options: AppSelectOption[];
  disabled?: boolean;
  rootClassName?: string;
  triggerClassName?: string;
  menuClassName?: string;
  optionClassName?: string;
  onChange: (value: string) => void;
};

export function AppSelect({
  ariaLabel,
  value,
  options,
  disabled = false,
  rootClassName = "",
  triggerClassName = "",
  menuClassName = "",
  optionClassName = "",
  onChange,
}: AppSelectProps) {
  const [open, setOpen] = useState(false);
  const [mounted, setMounted] = useState(false);
  const [menuStyle, setMenuStyle] = useState<CSSProperties>({ visibility: "hidden" });
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLUListElement>(null);
  const menuId = useId();

  const selectedOption = useMemo(
    () => options.find((option) => option.value === value) ?? options[0],
    [options, value],
  );

  const updateMenuPosition = useCallback(() => {
    if (!triggerRef.current) {
      return;
    }

    const rect = triggerRef.current.getBoundingClientRect();
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;
    const viewportPadding = 8;
    const gap = 6;
    const menuHeight = menuRef.current?.offsetHeight ?? 240;
    const width = Math.min(rect.width, viewportWidth - viewportPadding * 2);
    const left = Math.min(
      Math.max(viewportPadding, rect.left),
      Math.max(viewportPadding, viewportWidth - width - viewportPadding),
    );
    const spaceBelow = viewportHeight - rect.bottom - viewportPadding;
    const spaceAbove = rect.top - viewportPadding;
    const openUpwards = menuHeight > spaceBelow && spaceAbove > spaceBelow;
    const maxHeight = Math.max(120, (openUpwards ? spaceAbove : spaceBelow) - gap);
    const renderedHeight = Math.min(menuHeight, maxHeight);
    const top = openUpwards
      ? Math.max(viewportPadding, rect.top - renderedHeight - gap)
      : rect.bottom + gap;

    setMenuStyle({
      position: "fixed",
      top,
      left,
      width,
      maxHeight,
      visibility: "visible",
    });
  }, []);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (disabled) {
      setOpen(false);
    }
  }, [disabled]);

  useEffect(() => {
    setOpen(false);
  }, [value]);

  useEffect(() => {
    if (!open) {
      return;
    }

    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target as Node | null;
      if (!target) {
        return;
      }

      if (rootRef.current?.contains(target) || menuRef.current?.contains(target)) {
        return;
      }

      setOpen(false);
    };

    const handleKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
      }
    };

    const handleViewportChange = () => {
      updateMenuPosition();
    };

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    window.addEventListener("resize", handleViewportChange);
    window.addEventListener("scroll", handleViewportChange, true);

    setMenuStyle({ position: "fixed", visibility: "hidden" });
    const frameId = window.requestAnimationFrame(updateMenuPosition);

    return () => {
      window.cancelAnimationFrame(frameId);
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("resize", handleViewportChange);
      window.removeEventListener("scroll", handleViewportChange, true);
    };
  }, [open, updateMenuPosition]);

  const handleTriggerKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (disabled) {
      return;
    }

    if (event.key === "ArrowDown" || event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      setOpen((current) => !current);
    }
  };

  const menu = mounted && open
    ? createPortal(
        <ul
          className={`dialog-select-menu app-select-menu ${menuClassName}`.trim()}
          id={menuId}
          ref={menuRef}
          role="listbox"
          aria-label={`${ariaLabel}选项`}
          style={menuStyle}
        >
          {options.map((option) => {
            const active = option.value === value;

            return (
              <li key={`${ariaLabel}-${option.value || "__empty__"}`}>
                <button
                  className={`dialog-select-option app-select-option ${active ? "active" : ""} ${option.disabled ? "app-select-option-disabled" : ""} ${optionClassName}`.trim()}
                  type="button"
                  role="option"
                  aria-selected={active}
                  disabled={option.disabled}
                  onClick={() => {
                    if (option.disabled) {
                      return;
                    }
                    onChange(option.value);
                    setOpen(false);
                  }}
                >
                  <span className="app-select-label">{option.label}</span>
                </button>
              </li>
            );
          })}
        </ul>,
        document.body,
      )
    : null;

  return (
    <>
      <div className={`app-select-root ${rootClassName}`.trim()} data-open={open ? "true" : "false"} ref={rootRef}>
        <button
          aria-controls={open ? menuId : undefined}
          aria-expanded={open}
          aria-haspopup="listbox"
          aria-label={ariaLabel}
          className={`dialog-select-trigger app-select-trigger ${!value ? "dialog-select-empty" : ""} ${triggerClassName}`.trim()}
          disabled={disabled}
          ref={triggerRef}
          type="button"
          onClick={() => {
            if (!disabled) {
              setOpen((current) => !current);
            }
          }}
          onKeyDown={handleTriggerKeyDown}
        >
          <span className="app-select-label">{selectedOption?.label ?? ""}</span>
          <span className="dialog-select-caret" aria-hidden="true" />
        </button>
      </div>
      {menu}
    </>
  );
}
