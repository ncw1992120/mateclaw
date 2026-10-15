import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AloudataBrandMark from '../AloudataBrandMark.vue'

describe('AloudataBrandMark', () => {
  it('renders the Aloudata blue mark with an accessible label', () => {
    const wrapper = mount(AloudataBrandMark)

    expect(wrapper.attributes('aria-label')).toBe('Aloudata')
    expect(wrapper.find('svg').exists()).toBe(true)
    expect(wrapper.find('svg path').exists()).toBe(true)
  })
})
