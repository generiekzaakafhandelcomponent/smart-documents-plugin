/*
 * Copyright 2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {Component, EventEmitter, Input, OnDestroy, OnInit, Output} from '@angular/core';
import {FunctionConfigurationComponent} from '@valtimo/plugin';
import {BehaviorSubject, combineLatest, Observable, Subscription, take} from 'rxjs';
import {DocumentFormat, GenerateDocumentConfig, PayloadFormat} from '../../models';
import {ValuePathSelectorPrefix} from '@valtimo/components';

type SelectItem = {id: string; text: string};

/** How a field is configured: a fixed value from the dropdown, or a value resolver path in a text field. */
type ValueMode = 'FIXED' | 'VALUE_RESOLVER';

/**
 * The form fields of this component. `format` and `payloadFormat` each have a radio button (mode), a dropdown (fixed
 * value) and a text field (value resolver).
 */
interface GenerateDocumentFormValue
  extends Omit<GenerateDocumentConfig, 'format' | 'payloadFormat'> {
  formatMode?: ValueMode;
  formatSelect?: string;
  formatValueResolver?: string;
  payloadFormatMode?: ValueMode;
  payloadFormatSelect?: string;
  payloadFormatValueResolver?: string;
}

// A value resolver path starts with a prefix like doc:, pv:, case: or zaak:
const VALUE_RESOLVER_PATTERN = /^[a-zA-Z][\w-]*:\S+$/;

@Component({
  selector: 'valtimo-generate-document-configuration',
  templateUrl: './generate-document-configuration.component.html',
  styleUrls: ['./generate-document-configuration.component.scss'],
  standalone: false,
})
export class GenerateDocumentConfigurationComponent
  implements FunctionConfigurationComponent, OnInit, OnDestroy
{
  @Input() save$: Observable<void>;
  @Input() disabled$: Observable<boolean>;
  @Input() pluginId: string;
  @Input() prefillConfiguration$: Observable<GenerateDocumentConfig>;
  @Output() valid: EventEmitter<boolean> = new EventEmitter<boolean>();
  @Output() configuration: EventEmitter<GenerateDocumentConfig> =
    new EventEmitter<GenerateDocumentConfig>();

  readonly FORMATS: Array<DocumentFormat> = ['DOCX', 'HTML', 'PDF', 'XML'];
  readonly PAYLOAD_FORMATS: Array<PayloadFormat> = ['JSON', 'XML'];
  readonly DEFAULT_PAYLOAD_FORMAT: PayloadFormat = 'JSON';
  readonly FORMAT_SELECT_ITEMS: Array<SelectItem> = this.selectItems(this.FORMATS);
  readonly PAYLOAD_FORMAT_SELECT_ITEMS: Array<SelectItem> = this.selectItems(this.PAYLOAD_FORMATS);

  /** Whether the value resolver text field is shown instead of the dropdown. */
  formatUsesValueResolver = false;
  payloadFormatUsesValueResolver = false;

  public readonly ValuePathSelectorPrefix = ValuePathSelectorPrefix;

  private saveSubscription!: Subscription;
  private prefillSubscription?: Subscription;
  private readonly formValue$ = new BehaviorSubject<GenerateDocumentConfig | null>(null);
  private readonly valid$ = new BehaviorSubject<boolean>(false);

  ngOnInit(): void {
    this.prefillSubscription = this.prefillConfiguration$?.subscribe(prefill => {
      this.formatUsesValueResolver = this.isValueResolver(prefill?.format, this.FORMATS);
      this.payloadFormatUsesValueResolver = this.isValueResolver(
        prefill?.payloadFormat,
        this.PAYLOAD_FORMATS
      );
    });
    this.openSaveSubscription();
  }

  ngOnDestroy(): void {
    this.saveSubscription?.unsubscribe();
    this.prefillSubscription?.unsubscribe();
  }

  /** The radio button to select for a configured value. */
  mode(value: string | null | undefined, options: Array<string>): ValueMode {
    return this.isValueResolver(value, options) ? 'VALUE_RESOLVER' : 'FIXED';
  }

  /** The configured fixed value, to preselect it in the dropdown. */
  fixedValue(value: string | null | undefined, options: Array<string>): string | undefined {
    return value && options.includes(value) ? value : undefined;
  }

  /** The configured value resolver path, to prefill the text field. */
  valueResolverPath(value: string | null | undefined, options: Array<string>): string {
    return this.isValueResolver(value, options) ? (value as string) : '';
  }

  formValueChange(formValue: GenerateDocumentFormValue): void {
    this.formatUsesValueResolver = formValue.formatMode === 'VALUE_RESOLVER';
    this.payloadFormatUsesValueResolver = formValue.payloadFormatMode === 'VALUE_RESOLVER';

    const configuration = this.toConfiguration(formValue);
    this.formValue$.next(configuration);
    this.handleValid(configuration);
  }

  private toConfiguration(formValue: GenerateDocumentFormValue): GenerateDocumentConfig {
    const {
      formatMode,
      formatSelect,
      formatValueResolver,
      payloadFormatMode,
      payloadFormatSelect,
      payloadFormatValueResolver,
      ...rest
    } = formValue;
    return {
      ...rest,
      format: (this.formatUsesValueResolver ? formatValueResolver?.trim() : formatSelect) as DocumentFormat,
      payloadFormat: (this.payloadFormatUsesValueResolver
        ? payloadFormatValueResolver?.trim()
        : payloadFormatSelect) as PayloadFormat,
    };
  }

  private handleValid(configuration: GenerateDocumentConfig): void {
    const valid = !!(
      configuration.templateGroup &&
      configuration.templateName &&
      configuration.format &&
      configuration.resultingDocumentProcessVariableName &&
      configuration.templateData?.length > 0 &&
      (!this.formatUsesValueResolver || VALUE_RESOLVER_PATTERN.test(configuration.format)) &&
      (!this.payloadFormatUsesValueResolver ||
        VALUE_RESOLVER_PATTERN.test(configuration.payloadFormat ?? ''))
    );

    this.valid$.next(valid);
    this.valid.emit(valid);
  }

  private isValueResolver(value: string | null | undefined, options: Array<string>): boolean {
    return !!value && !options.includes(value);
  }

  private selectItems(options: Array<string>): Array<SelectItem> {
    return options.map(option => ({id: option, text: option}));
  }

  private openSaveSubscription(): void {
    this.saveSubscription = this.save$?.subscribe(save => {
      combineLatest([this.formValue$, this.valid$])
        .pipe(take(1))
        .subscribe(([formValue, valid]) => {
          if (valid) {
            this.configuration.emit(formValue);
          }
        });
    });
  }
}
